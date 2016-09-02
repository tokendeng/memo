package org.apache.spark.memo.deploy.master.ui

import org.apache.spark.Logging
import org.apache.spark.memo.deploy.master.MemoMaster
import org.apache.spark.memo.ui.MemoJettyUtils._
import org.apache.spark.memo.ui.{MemoJettyUtils, MemoWebUI}
import org.eclipse.jetty.servlet.{ServletHolder, DefaultServlet, ServletContextHandler}

/**
 * Created by dengchangchun on 2016/8/19.
 */

private[master] class MemoMasterWebUI(val master: MemoMaster, requestedPort: Int)
  extends MemoWebUI(master.securityMgr, requestedPort, master.conf, name = "MemoMasterUI") with Logging
  {

  val masterEndpointRef = master.self
  val killEnabled = master.conf.getBoolean("spark.memo.ui.killEnabled", true)

  val masterPage = new MemoMasterPage(this)

  initialize()

  /** Initialize all components of the server. */
  def initialize() {
    val masterPage = new MemoMasterPage(this)
    attachPage(masterPage)
    val handler:ServletContextHandler = MemoJettyUtils.createStaticHandler(MemoMasterWebUI.STATIC_RESOURCE_DIR, "/static")
    attachHandler(handler)

  }

}

private[master] object MemoMasterWebUI{
  private val STATIC_RESOURCE_DIR = "org/apache/spark/memo/ui/static"

}
