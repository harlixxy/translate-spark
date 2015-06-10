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

package org.apache.spark.mllib.regression

import scala.beans.BeanInfo

import org.apache.spark.mllib.linalg.{Vectors, Vector}
import org.apache.spark.mllib.util.NumericParser
import org.apache.spark.SparkException

/**
 * Class that represents the features and labels of a data point.
 *
 * @param label Label for this data point.
 * @param features List of features for this data point.
 *
 * 表示一个数据点的特性和标签的类。
 * label ： 该数据点的标签
 * features ：该数据点的特征列表
 */
@BeanInfo
case class LabeledPoint(label: Double, features: Vector) {
  override def toString: String = {
    "(%s,%s)".format(label, features)
  }
}

/**
 * Parser for [[org.apache.spark.mllib.regression.LabeledPoint]].
 *
 * LabeledPoint 的解析器，可以从对应字符串转换得到该类。
 * 说明：可以通过 unapply 自动解析
 */
object LabeledPoint {
  /**
   * Parses a string resulted from `LabeledPoint#toString` into
   * an [[org.apache.spark.mllib.regression.LabeledPoint]].
   */
  def parse(s: String): LabeledPoint = {
    if (s.startsWith("(")) {
      NumericParser.parse(s) match {
        case Seq(label: Double, numeric: Any) =>
          LabeledPoint(label, Vectors.parseNumeric(numeric))
        case other =>
          throw new SparkException(s"Cannot parse $other.")
      }
    } else { // dense format used before v1.0  在 v1.0 版本之前使用的密集型格式
      val parts = s.split(',')
      val label = java.lang.Double.parseDouble(parts(0))
      val features = Vectors.dense(parts(1).trim().split(' ').map(java.lang.Double.parseDouble))
      LabeledPoint(label, features)
    }
  }
}
