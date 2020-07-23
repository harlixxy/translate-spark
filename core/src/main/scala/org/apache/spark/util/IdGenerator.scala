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

package org.apache.spark.util

import java.util.concurrent.atomic.AtomicInteger

/**
 * A util used to get a unique generation ID. This is a wrapper around Java's
 * AtomicInteger. An example usage is in BlockManager, where each BlockManager
 * instance would start an Akka actor and we use this utility to assign the Akka
 * actors unique names.
 *  一个工具用来得到一个唯一的生成ID.这是在Java的AtomicInteger包装。一个例子是在
 *  使用BlockManager，每个BlockManager实例都会启动一个Akka actor和我们使用这个工具
 *  赋给Akka Actors唯一的名字.
 */
private[spark] class IdGenerator {
  private var id = new AtomicInteger
  def next: Int = id.incrementAndGet
}
