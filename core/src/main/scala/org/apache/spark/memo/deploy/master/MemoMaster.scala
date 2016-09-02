package org.apache.spark.memo.deploy.master

import java.util

import com.hikvision.bigdata.memo.annotation.{SimilarAs, Dependence}
import org.apache.spark.deploy.master.ui.MasterWebUI
import org.apache.spark.memo.deploy.master.ui.MemoMasterWebUI

import scala.collection.mutable.{ArrayBuffer, HashMap, HashSet}

import org.apache.spark.memo.MemoException
import org.apache.spark.memo.deploy.DeployMessages._
import org.apache.spark.memo.deploy.master.MemoMasterArguments
import org.apache.spark.memo.deploy.master.MemoMasterMessages.{BoundPortsRequest, BoundPortsResponse}
import org.apache.spark.rpc.{RpcCallContext, ThreadSafeRpcEndpoint, RpcAddress, RpcEnv}
import org.apache.spark.util.Utils
import org.apache.spark.{SparkException, SecurityManager, SparkConf, Logging}

import scala.collection.mutable

/**
 * Created by dengchangchun on 2016/8/17.
 */

private[deploy] class MemoMaster(
                              override val rpcEnv: RpcEnv,
                              address: RpcAddress,
                              webUiPort: Int,
                              val securityMgr: SecurityManager,
                              val conf: SparkConf)
  extends ThreadSafeRpcEndpoint with Logging{

  val containers = new HashSet[ContainerInfo]

  private val idToContainer = new HashMap[String, ContainerInfo]
  private val addressToContainer = new HashMap[RpcAddress, ContainerInfo]

  Utils.checkHost(address.host, "Expected hostname")

  // After onStart, webUi will be set
  private var webUi: MemoMasterWebUI = null

  private val masterPublicAddress = {
    val envVar = conf.getenv("SPARK_PUBLIC_DNS")
    if (envVar != null) envVar else address.host
  }

  private val masterUrl = address.toSparkURL
  private var masterWebUiUrl: String = _

  private var state = MemoRecoveryState.ALIVE

  // After onStart, webUi will be set
//  private var webUi: MasterWebUI = null

//  Alternative application submission gateway that is stable across Spark versions
  private val restServerEnabled = conf.getBoolean("spark.master.rest.enabled", true)
//  private var restServer: Option[StandaloneRestServer] = None
  private var restServerBoundPort: Option[Int] = None

  override def onStart(): Unit = {
    logInfo("Starting Spark Memo master at " + masterUrl)
    logInfo(s"Running Spark Memo version ${org.apache.spark.memo.MEMO_VERSION}")
    webUi = new MemoMasterWebUI(this, webUiPort)
    webUi.bind()
    masterWebUiUrl = "http://" + masterPublicAddress + ":" + webUi.boundPort
  }

  override def receive: PartialFunction[Any, Unit] = {
    case RegisterContainer(id, containerHost, containerPort, containerRef, memory, containerUiPort, publicAddress) => {
      logInfo("Registering worker %s:%d with %s RAM".format(
        containerHost, containerPort, Utils.megabytesToString(memory)))

      if (state == MemoRecoveryState.STANDBY) {
        // ignore, don't send response
      } else if (idToContainer.contains(id)) {
        containerRef.send(RegisterContainerFailed("Duplicate container ID"))
      } else {
        val container = new ContainerInfo(id, containerHost, containerPort, memory,
          containerRef, containerUiPort, publicAddress)
        if (registerContainer(container)) {
          containerRef.send(RegisteredContainer(self, masterWebUiUrl))
        } else {
          val workerAddress = container.endpoint.address
          logWarning("Worker registration failed. Attempted to re-register worker at same " +
            "address: " + workerAddress)
          containerRef.send(RegisterContainerFailed("Attempted to re-register worker at same address: "
            + workerAddress))
        }
      }
    }

    case Heartbeat(containerId, container) => {
      idToContainer.get(containerId) match {
        case Some(workerInfo) =>
          workerInfo.lastHeartbeat = System.currentTimeMillis()
        case None =>
          if (containers.map(_.id).contains(containerId)) {
            logWarning(s"Got heartbeat from unregistered container $containerId." +
              " Asking it to re-register.")
            container.send(ReconnectContainer(masterUrl))
          } else {
            logWarning(s"Got heartbeat from unregistered container $containerId." +
              " This container was never registered, so ignoring the heartbeat.")
          }
      }
    }



  }

  override def receiveAndReply(context: RpcCallContext): PartialFunction[Any, Unit] = {
    case RequestMasterState => {
      context.reply(MasterStateResponse(
        address.host, address.port, restServerBoundPort,
        containers.toArray,  state))
    }
    case BoundPortsRequest => {
      context.reply(BoundPortsResponse(address.port, webUi.boundPort, restServerBoundPort))
    }
  }
  override def onDisconnected(remoteAddress: RpcAddress): Unit = {
    logInfo(s"$address got disassociated, removing it.")
  }


  override def onStop(): Unit = {

  }

  @SimilarAs( method = "registerWorker")
  private def registerContainer(container: ContainerInfo): Boolean = {
    // There may be one or more refs to dead workers on this same node (w/ different ID's),
    // remove them.
    containers.filter { c =>
      (c.host == container.host && c.port == container.port) && (c.state == ContainerState.DEAD)
    }.foreach { c =>
      containers -= c
    }

    val containerAddress = container.endpoint.address
    if (addressToContainer.contains(containerAddress)) {
      val oldContainer = addressToContainer(containerAddress)
      if (oldContainer.state == ContainerState.UNKNOWN) {
        // A worker registering from UNKNOWN implies that the worker was restarted during recovery.
        // The old worker must thus be dead, so we will remove it and accept the new worker.
        removeContainer(oldContainer)
      } else {
        logInfo("Attempted to re-register worker at same address: " + containerAddress)
        return false
      }
    }

    containers += container
    idToContainer(container.id) = container
    addressToContainer(containerAddress) = container
    true
  }

  @SimilarAs( method = "removeWorker")
  private def removeContainer(container: ContainerInfo){
    logInfo("Removing worker " + container.id + " on " + container.host + ":" + container.port)
    container.setState(ContainerState.DEAD)
    idToContainer -= container.id
    addressToContainer -= container.endpoint.address
  }


}

@Dependence(project="Spark", module="core", clazz="SparkConf")
private[deploy] object MemoMaster extends Logging{
  val SYSTEM_NAME = "memoMaster"
  val ENDPOINT_NAME = "Master"

  def main(argStrings: Array[String]) {

    val conf = new SparkConf
    val args = new MemoMasterArguments(argStrings, conf)
    val (rpcEnv, _, _) = startRpcEnvAndEndpoint(args.host, args.port, args.webUiPort, conf)
    rpcEnv.awaitTermination()
  }

  /**
   * Start the Master and return a three tuple of:
   *   (1) The Master RpcEnv
   *   (2) The web UI bound port
   *   (3) The REST server bound port, if any
   */
  @SimilarAs( clazz ="Master", method = "startRpcEnvAndEndpoint")
  def startRpcEnvAndEndpoint(
                              host: String,
                              port: Int,
                              webUiPort: Int,
                              conf: SparkConf): (RpcEnv, Int, Option[Int]) = {
    val securityMgr = new SecurityManager(conf)
    val rpcEnv = RpcEnv.create(SYSTEM_NAME, host, port, conf, securityMgr)
    val masterEndpoint = rpcEnv.setupEndpoint(ENDPOINT_NAME,
      new MemoMaster(rpcEnv, rpcEnv.address, webUiPort, securityMgr, conf))
    val portsResponse = masterEndpoint.askWithRetry[BoundPortsResponse](BoundPortsRequest)
    (rpcEnv, portsResponse.webUIPort, portsResponse.restPort)
  }
}
