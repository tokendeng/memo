package org.apache.spark.memo.deploy

import org.apache.spark.memo.deploy.master.ContainerInfo
import org.apache.spark.memo.deploy.master.MemoRecoveryState.MemoMasterState
import org.apache.spark.rpc.RpcEndpointRef
import org.apache.spark.util.Utils

/**
 * Created by dengchangchun on 2016/8/17.
 */

sealed trait DeployMessage extends Serializable

/** Contains messages sent between MemoMaster and Containers. */
object DeployMessages {

  // Container to MemoMaster

  case class RegisterContainer(
                             id: String,
                             host: String,
                             port: Int,
                             container: RpcEndpointRef,
                             memory: Int,
                             webUiPort: Int,
                             publicAddress: String)
    extends DeployMessage {
    Utils.checkHost(host, "Required hostname")
    assert (port > 0)
  }

  case class Heartbeat(containerId: String, worker: RpcEndpointRef) extends DeployMessage

  // MemoMaster to Container

  case class RegisteredContainer(master: RpcEndpointRef, masterWebUiUrl: String) extends DeployMessage

  case class RegisterContainerFailed(message: String) extends DeployMessage

  case class ReconnectContainer(masterUrl: String) extends DeployMessage

  // Master to Container & AppClient

  case class MasterChanged(master: RpcEndpointRef, masterWebUiUrl: String)

  // Container internal

  case object ReregisterWithMaster // used when a worker attempts to reconnect to a master

  // MasterWebUI To Master

  case object RequestMasterState

  // Master to MasterWebUI

  case class MasterStateResponse(
                                  host: String,
                                  port: Int,
                                  restPort: Option[Int],
                                  containers: Array[ContainerInfo],
                                  status: MemoMasterState) {

    Utils.checkHost(host, "Required hostname")
    assert (port > 0)

    def uri: String = "memo://" + host + ":" + port
    def restUri: Option[String] = restPort.map { p => "memo://" + host + ":" + p }
  }

  //  ContainerWebUI to Container

  case object RequestContainerState

  // Container to ContainerWebUI

  case class ContainerStateResponse(host: String, port: Int, containerId: String, masterUrl: String,
                                    memory: Int,memoryUsed: Int, masterWebUiUrl: String) {

    Utils.checkHost(host, "Required hostname")
    assert (port > 0)
  }

  // Liveness checks in various places

  case object SendHeartbeat

}
