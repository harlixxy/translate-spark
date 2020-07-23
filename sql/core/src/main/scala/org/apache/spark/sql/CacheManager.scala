/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql

import java.util.concurrent.locks.ReentrantReadWriteLock

import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.columnar.InMemoryRelation
import org.apache.spark.storage.StorageLevel
import org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK

/** Holds a cached logical plan and its data */
private case class CachedData(plan: LogicalPlan, cachedRepresentation: InMemoryRelation)

/**
 * Provides support in a SQLContext for caching query results and automatically using these cached
 * results when subsequent queries are executed.  Data is cached using byte buffers stored in an
 * InMemoryRelation.  This relation is automatically substituted query plans that return the
 * `sameResult` as the originally cached query.
 *
 * 为 SQLContext 提供支持，可以缓存查询的结果，并在后续查询执行时自动使用这些缓存结果。
 * 数据使用存储在 InMemoryRelation 中的 byte buffers进行缓存。
 * 该 relation 会自动替换为返回和原始缓存的查询“相同结果”的查询计划，
 */
private[sql] trait CacheManager {
  self: SQLContext =>

  @transient
  private val cachedData = new scala.collection.mutable.ArrayBuffer[CachedData]

  @transient
  private val cacheLock = new ReentrantReadWriteLock

  /** Returns true if the table is currently cached in-memory. */
  def isCached(tableName: String): Boolean = lookupCachedData(table(tableName)).nonEmpty

  /** Caches the specified table in-memory. */
  def cacheTable(tableName: String): Unit = cacheQuery(table(tableName), Some(tableName))

  /** Removes the specified table from the in-memory cache. */
  def uncacheTable(tableName: String): Unit = uncacheQuery(table(tableName))

  /** Acquires a read lock on the cache for the duration of `f`. */
  private def readLock[A](f: => A): A = {
    val lock = cacheLock.readLock()
    lock.lock()
    try f finally {
      lock.unlock()
    }
  }

  /** Acquires a write lock on the cache for the duration of `f`. */
  private def writeLock[A](f: => A): A = {
    val lock = cacheLock.writeLock()
    lock.lock()
    try f finally {
      lock.unlock()
    }
  }

  private[sql] def clearCache(): Unit = writeLock {
    cachedData.foreach(_.cachedRepresentation.cachedColumnBuffers.unpersist())
    cachedData.clear()
  }

  /**
   * Caches the data produced by the logical representation of the given schema rdd.  Unlike
   * `RDD.cache()`, the default storage level is set to be `MEMORY_AND_DISK` because recomputing
   * the in-memory columnar representation of the underlying table is expensive.
   *
   * 缓存指定 schema rdd 的逻辑表达式所构建的数据。和 `RDD.cache()` 不同，
   * 由于底层表的内存列式表示的重计算代价很高，默认的存储级别被设置为 `MEMORY_AND_DISK`。
   */
  private[sql] def cacheQuery(
      query: SchemaRDD,
      tableName: Option[String] = None,
      storageLevel: StorageLevel = MEMORY_AND_DISK): Unit = writeLock {
    val planToCache = query.queryExecution.analyzed
    if (lookupCachedData(planToCache).nonEmpty) {
      logWarning("Asked to cache already cached data.")
    } else {
      cachedData +=
        CachedData(
          planToCache,
          InMemoryRelation(
            useCompression,
            columnBatchSize,
            storageLevel,
            query.queryExecution.executedPlan,
            tableName))
    }
  }

  /** Removes the data for the given SchemaRDD from the cache */
  private[sql] def uncacheQuery(query: SchemaRDD, blocking: Boolean = true): Unit = writeLock {
    val planToCache = query.queryExecution.analyzed
    val dataIndex = cachedData.indexWhere(cd => planToCache.sameResult(cd.plan))
    require(dataIndex >= 0, s"Table $query is not cached.")
    cachedData(dataIndex).cachedRepresentation.cachedColumnBuffers.unpersist(blocking)
    cachedData.remove(dataIndex)
  }

  /** Tries to remove the data for the given SchemaRDD from the cache if it's cached */
  private[sql] def tryUncacheQuery(
      query: SchemaRDD,
      blocking: Boolean = true): Boolean = writeLock {
    val planToCache = query.queryExecution.analyzed
    val dataIndex = cachedData.indexWhere(cd => planToCache.sameResult(cd.plan))
    val found = dataIndex >= 0
    if (found) {
      cachedData(dataIndex).cachedRepresentation.cachedColumnBuffers.unpersist(blocking)
      cachedData.remove(dataIndex)
    }
    found
  }

  /** Optionally returns cached data for the given SchemaRDD */
  private[sql] def lookupCachedData(query: SchemaRDD): Option[CachedData] = readLock {
    lookupCachedData(query.queryExecution.analyzed)
  }

  /** Optionally returns cached data for the given LogicalPlan. */
  private[sql] def lookupCachedData(plan: LogicalPlan): Option[CachedData] = readLock {
    cachedData.find(cd => plan.sameResult(cd.plan))
  }

  /** Replaces segments of the given logical plan with cached versions where possible. */
  private[sql] def useCachedData(plan: LogicalPlan): LogicalPlan = {
    plan transformDown {
      case currentFragment =>
        lookupCachedData(currentFragment)
          .map(_.cachedRepresentation.withOutput(currentFragment.output))
          .getOrElse(currentFragment)
    }
  }

  /**
   * Invalidates the cache of any data that contains `plan`. Note that it is possible that this
   * function will over invalidate.
   */
  private[sql] def invalidateCache(plan: LogicalPlan): Unit = writeLock {
    cachedData.foreach {
      case data if data.plan.collect { case p if p.sameResult(plan) => p }.nonEmpty =>
        data.cachedRepresentation.recache()
      case _ =>
    }
  }
}
