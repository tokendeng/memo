package org.apache.spark.memo.deploy.container.ui

import javax.servlet.http.HttpServletRequest

import org.apache.spark.Logging
import org.apache.spark.memo.deploy.container.Container
import org.apache.spark.memo.ui.{MemoJettyUtils, MemoWebUI}
import org.apache.spark.ui.WebUI
import org.apache.spark.util.RpcUtils

/**
 * Created by dengchangchun on 2016/8/22.
 */
private[container] class ContainerWebUI(
                                         val container: Container,
                                         requestedPort: Int)
  extends MemoWebUI(container.securityMgr, requestedPort, container.conf, name = "ContainerUI")
  with Logging {

  private[ui] val timeout = RpcUtils.askRpcTimeout(container.conf)

  initialize()

  /** Initialize all components of the server. */
  def initialize() {
    attachPage(new ContainerPage(this))
    attachHandler(MemoJettyUtils.createStaticHandler(ContainerWebUI.STATIC_RESOURCE_DIR, "/static"))

  }
}

private[container] object ContainerWebUI{
  private val STATIC_RESOURCE_DIR = "org/apache/spark/memo/ui/static"
}

