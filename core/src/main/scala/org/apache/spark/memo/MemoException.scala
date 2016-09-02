package org.apache.spark.memo

import com.hikvision.bigdata.memo.annotation.SimilarAs

/**
 * Created by dengchangchun on 2016/8/17.
 */
@SimilarAs( clazz ="SparkException")
class MemoException(message: String, cause: Throwable)
  extends Exception(message, cause) {

  def this(message: String) = this(message, null)
}
