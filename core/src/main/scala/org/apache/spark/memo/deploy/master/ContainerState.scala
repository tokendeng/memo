package org.apache.spark.memo.deploy.master

import com.hikvision.bigdata.memo.annotation.SimilarAs


/**
 * Created by dengchangchun on 2016/8/17.
 */
@SimilarAs( clazz ="WorkerState")
private[master] object ContainerState extends Enumeration {
  type ContainerState = Value

  val ALIVE, DEAD, DECOMMISSIONED, UNKNOWN = Value
}
