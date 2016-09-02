package org.apache.spark.memo.deploy

import org.apache.spark.memo.deploy.container.Container
import org.apache.spark.memo.deploy.master.MemoMaster
import org.apache.spark.rpc.RpcEnv
import org.apache.spark.util.Utils
import org.apache.spark.{Logging, SparkConf}

import scala.collection.mutable.ArrayBuffer

/**
 * Created by dengchangchun on 2016/8/18.
 */
private[memo] class LocalMemoCluster (
                                       numContainers: Int,
                                       memoryPerContainer: Int,
                                       conf: SparkConf)
  extends Logging {

  private val localHostname = Utils.localHostName()
  private val masterRpcEnvs = ArrayBuffer[RpcEnv]()
  private val containerRpcEnvs = ArrayBuffer[RpcEnv]()
  // exposed for testing
  var masterWebUIPort = -1

  def start(): Array[String] = {
    logInfo("Starting a local Memo cluster with " + numContainers + " containers.")

    // Disable REST server on Master in this mode unless otherwise specified
    val _conf = conf.clone()
      .setIfMissing("spark.master.rest.enabled", "false")
      .set("spark.shuffle.service.enabled", "false")

    /* Start the Master */
    val (rpcEnv, webUiPort, _) = MemoMaster.startRpcEnvAndEndpoint(localHostname, 0, 0, _conf)
    masterWebUIPort = webUiPort
    masterRpcEnvs += rpcEnv
    val masterUrl = "spark://" + Utils.localHostNameForURI() + ":" + rpcEnv.address.port
    val masters = Array(masterUrl)

    /* Start the Workers */
    for (workerNum <- 1 to numContainers) {
      val containerEnv = Container.startRpcEnvAndEndpoint(localHostname, 0, 0,
        memoryPerContainer, masters,Some(workerNum), _conf)
      containerRpcEnvs += containerEnv
    }

    masters
  }

  def stop() {
    logInfo("Shutting down local Spark cluster.")
    // Stop the workers before the master so they don't get upset that it disconnected
    containerRpcEnvs.foreach(_.shutdown())
    masterRpcEnvs.foreach(_.shutdown())
    masterRpcEnvs.clear()
    containerRpcEnvs.clear()
  }

}
