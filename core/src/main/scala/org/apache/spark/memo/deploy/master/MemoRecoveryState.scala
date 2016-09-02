package org.apache.spark.memo.deploy.master

import com.hikvision.bigdata.memo.annotation.SimilarAs

/**
 * Created by dengchangchun on 2016/8/17.
 */
@SimilarAs( clazz = "RecoveryState")
private[deploy] object MemoRecoveryState extends Enumeration {
  type MemoMasterState = Value

  val STANDBY, ALIVE, RECOVERING, COMPLETING_RECOVERY = Value
}
