package org.apache.spark.memo.deploy.container.ui

import javax.servlet.http.HttpServletRequest

import org.apache.spark.memo.deploy.DeployMessages.{ContainerStateResponse, RequestContainerState}
import org.apache.spark.memo.deploy.MemoJsonProtocol
import org.apache.spark.memo.ui.{MemoUIUtils, MemoWebUIPage}
import org.apache.spark.ui.UIUtils
import org.apache.spark.util.Utils
import org.json4s.JValue

import scala.xml.Node

/**
 * Created by dengchangchun on 2016/8/22.
 */
private[ui] class ContainerPage(parent: ContainerWebUI) extends MemoWebUIPage("") {
  private val containerEndpoint = parent.container.self

  override def renderJson(request: HttpServletRequest): JValue = {
    val containerState = containerEndpoint.askWithRetry[ContainerStateResponse](RequestContainerState)
    MemoJsonProtocol.writeContainerState(containerState)
  }

  def render(request: HttpServletRequest): Seq[Node] = {
    val containerState = containerEndpoint.askWithRetry[ContainerStateResponse](RequestContainerState)


    // For now we only show driver information if the user has submitted drivers to the cluster.
    // This is until we integrate the notion of drivers and applications in the UI.

    val content =
      <div class="row-fluid"> <!-- Worker Details -->
        <div class="span12">
          <ul class="unstyled">
            <li><strong>ID:</strong> {containerState.containerId}</li>
            <li><strong>
              Master URL:</strong> {containerState.masterUrl}
            </li>
            <li><strong>Memory:</strong> {Utils.megabytesToString(containerState.memory)}
              ({Utils.megabytesToString(containerState.memoryUsed)} Used)</li>
          </ul>
          <p><a href={containerState.masterWebUiUrl}>Back to Master</a></p>
        </div>
      </div>;
    MemoUIUtils.basicSparkPage(content, "Memo Container at %s:%s".format(
      containerState.host, containerState.port))
  }


}
