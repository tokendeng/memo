package org.apache.spark.memo.deploy.master

import com.hikvision.bigdata.memo.annotation.SimilarAs
import org.apache.spark.deploy.master.WorkerState
import org.apache.spark.rpc.RpcEndpointRef
import org.apache.spark.util.Utils

import scala.collection.mutable

/**
 * Created by dengchangchun on 2016/8/17.
 */
@SimilarAs( clazz ="WorkerInfo")
private[memo]class
ContainerInfo(
                                  val id: String,
                                  val host: String,
                                  val port: Int,
                                  val memory: Int,
                                  val endpoint: RpcEndpointRef,
                                  val webUiPort: Int,
                                  val publicAddress: String)
  extends Serializable {

  Utils.checkHost(host, "Expected hostname")
  assert (port > 0)

  @transient var state: ContainerState.Value = _
  @transient var memoryUsed: Int = _

  @transient var lastHeartbeat: Long = _

  init()

  def memoryFree: Int = memory - memoryUsed

  private def readObject(in: java.io.ObjectInputStream): Unit = Utils.tryOrIOException {
    in.defaultReadObject()
    init()
  }

  private def init() {
    state = ContainerState.ALIVE
    memoryUsed = 0
    lastHeartbeat = System.currentTimeMillis()
  }

  def hostPort: String = {
    assert (port > 0)
    host + ":" + port
  }

  def webUiAddress : String = {
    "http://" + this.publicAddress + ":" + this.webUiPort
  }

  def setState(state: ContainerState.Value): Unit = {
    this.state = state
  }

  def isAlive(): Boolean = this.state == ContainerState.ALIVE
}
