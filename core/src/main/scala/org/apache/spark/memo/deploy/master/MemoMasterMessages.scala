package org.apache.spark.memo.deploy.master

import com.hikvision.bigdata.memo.annotation.SimilarAs
import org.apache.spark.deploy.master.{WorkerInfo, ApplicationInfo}

/**
 * Created by dengchangchun on 2016/8/17.
 */
@SimilarAs( clazz ="MasterMessages")
sealed trait MemoMasterMessages extends Serializable

/** Contains messages seen only by the Master and its associated entities. */
@SimilarAs( clazz ="MasterMessages")
private[master] object MemoMasterMessages {

  // LeaderElectionAgent to Master

  case object ElectedLeader

  case object RevokedLeadership

  // Master to itself

  case object CheckForWorkerTimeOut

  case class BeginRecovery(storedApps: Seq[ApplicationInfo], storedWorkers: Seq[WorkerInfo])

  case object CompleteRecovery

  case object BoundPortsRequest

  case class BoundPortsResponse(rpcEndpointPort: Int, webUIPort: Int, restPort: Option[Int])
}

