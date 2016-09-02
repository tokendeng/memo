package org.apache.spark.memo.deploy

import org.apache.spark.memo.deploy.DeployMessages.{ContainerStateResponse, MasterStateResponse}
import org.apache.spark.memo.deploy.master.ContainerInfo
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._

/**
 * Created by dengchangchun on 2016/8/19.
 */
private[deploy] object MemoJsonProtocol {

  def writeContainerInfo(obj: ContainerInfo): JObject = {
    ("id" -> obj.id) ~
      ("host" -> obj.host) ~
      ("port" -> obj.port) ~
      ("webuiaddress" -> obj.webUiAddress) ~
      ("memory" -> obj.memory) ~
      ("memoryused" -> obj.memoryUsed) ~
      ("memoryfree" -> obj.memoryFree) ~
      ("state" -> obj.state.toString) ~
      ("lastheartbeat" -> obj.lastHeartbeat)
  }

  def writeMasterState(obj: MasterStateResponse): JObject = {
    val aliveWorkers = obj.containers.filter(_.isAlive())
    ("url" -> obj.uri) ~
      ("workers" -> obj.containers.toList.map(writeContainerInfo)) ~
      ("memory" -> aliveWorkers.map(_.memory).sum) ~
      ("memoryused" -> aliveWorkers.map(_.memoryUsed).sum) ~
      ("status" -> obj.status.toString)
  }

  def writeContainerState(obj: ContainerStateResponse): JObject = {
    ("id" -> obj.containerId) ~
      ("masterurl" -> obj.masterUrl) ~
      ("masterwebuiurl" -> obj.masterWebUiUrl) ~
      ("memory" -> obj.memory) ~
      ("memoryused" -> obj.memoryUsed)
  }

}
