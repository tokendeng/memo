package org.apache.spark.memo.deploy.master.ui

import javax.servlet.http.HttpServletRequest

import org.apache.spark.memo.deploy.master.{ContainerInfo, ContainerState}
import org.apache.spark.memo.ui.{MemoUIUtils, MemoWebUIPage}
import org.apache.spark.util.Utils

import scala.xml.Node

import org.apache.spark.memo.deploy.DeployMessages.{MasterStateResponse, RequestMasterState}
import org.apache.spark.memo.deploy.MemoJsonProtocol
import org.apache.spark.ui.{UIUtils, WebUIPage}
import org.json4s.JValue

/**
 * Created by dengchangchun on 2016/8/19.
 */
private[ui] class MemoMasterPage(parent: MemoMasterWebUI) extends MemoWebUIPage("") {
  private val master = parent.masterEndpointRef

  def getMasterState: MasterStateResponse = {
    master.askWithRetry[MasterStateResponse](RequestMasterState)
  }

  override def renderJson(request: HttpServletRequest): JValue = {
    MemoJsonProtocol.writeMasterState(getMasterState)
  }

  /** Index view listing applications and executors */
  def render(request: HttpServletRequest): Seq[Node] = {
    val state = getMasterState

    val containerHeaders = Seq("Container Id", "Address", "State", "Memory")
    val containers = state.containers.sortBy(_.id)
    val aliveContainers = state.containers.filter(_.state == ContainerState.ALIVE)
    val containerTable = UIUtils.listingTable(containerHeaders, containerRow, containers)


    val content =
      <div class="row-fluid">
        <div class="span12">
          <ul class="unstyled">
            <li><strong>URL:</strong> {state.uri}</li>
            {
            state.restUri.map { uri =>
              <li>
                <strong>REST URL:</strong> {uri}
                <span class="rest-uri"> (cluster mode)</span>
              </li>
            }.getOrElse { Seq.empty }
            }
            <li><strong>Alive Workers:</strong> {aliveContainers.size}</li>
            <li><strong>Memory in use:</strong>
              {Utils.megabytesToString(aliveContainers.map(_.memory).sum)} Total,
              {Utils.megabytesToString(aliveContainers.map(_.memoryUsed).sum)} Used</li>

            <li><strong>Status:</strong> {state.status}</li>
          </ul>
        </div>
      </div>

        <div class="row-fluid">
          <div class="span12">
            <h4> Containers </h4>
            {containerTable}
          </div>
        </div>;



    MemoUIUtils.basicSparkPage(content, "Memo Master at " + state.uri)
  }

  private def containerRow(worker: ContainerInfo): Seq[Node] = {
    <tr>
      <td>
        <a href={worker.webUiAddress}>{worker.id}</a>
      </td>
      <td>{worker.host}:{worker.port}</td>
      <td>{worker.state}</td>
      <td sorttable_customkey={"%s.%s".format(worker.memory, worker.memoryUsed)}>
        {Utils.megabytesToString(worker.memory)}
        ({Utils.megabytesToString(worker.memoryUsed)} Used)
      </td>
    </tr>
  }
}
