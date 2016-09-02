package org.apache.spark.memo.ui


import javax.servlet.http.HttpServletRequest

import com.hikvision.bigdata.memo.annotation.SimilarAs
import org.apache.spark.memo.ui.{MemoJettyUtils, ServerInfo}

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.xml.Node

import org.eclipse.jetty.servlet.ServletContextHandler
import org.json4s.JsonAST.{JNothing, JValue}

import org.apache.spark.memo.ui.MemoJettyUtils._
import org.apache.spark.util.Utils
import org.apache.spark.{Logging, SecurityManager, SparkConf}

/**
 * Created by dengchangchun on 2016/8/29.
 */
@SimilarAs(clazz = "WebUI")
private[spark] abstract class MemoWebUI(
                                     val securityManager: SecurityManager,
                                     port: Int,
                                     conf: SparkConf,
                                     basePath: String = "",
                                     name: String = "")
  extends Logging {

  protected val tabs = ArrayBuffer[MemoWebUITab]()
  protected val handlers = ArrayBuffer[ServletContextHandler]()
  protected val pageToHandlers = new HashMap[MemoWebUIPage, ArrayBuffer[ServletContextHandler]]
  protected var serverInfo: Option[ServerInfo] = None
  protected val localHostName = Utils.localHostNameForURI()
  protected val publicHostName = Option(conf.getenv("SPARK_PUBLIC_DNS")).getOrElse(localHostName)
  private val className = Utils.getFormattedClassName(this)

  def getBasePath: String = basePath
  def getTabs: Seq[MemoWebUITab] = tabs.toSeq
  def getHandlers: Seq[ServletContextHandler] = handlers.toSeq
  def getSecurityManager: SecurityManager = securityManager

  /** Attach a tab to this UI, along with all of its attached pages. */
  def attachTab(tab: MemoWebUITab) {
    tab.pages.foreach(attachPage)
    tabs += tab
  }

  def detachTab(tab: MemoWebUITab) {
    tab.pages.foreach(detachPage)
    tabs -= tab
  }

  def detachPage(page: MemoWebUIPage) {
    pageToHandlers.remove(page).foreach(_.foreach(detachHandler))
  }

  /** Attach a page to this UI. */
  def attachPage(page: MemoWebUIPage) {
    val pagePath = "/" + page.prefix
    val renderHandler = createServletHandler(pagePath,
      (request: HttpServletRequest) => page.render(request), securityManager, basePath)
    val renderJsonHandler = createServletHandler(pagePath.stripSuffix("/") + "/json",
      (request: HttpServletRequest) => page.renderJson(request), securityManager, basePath)
    attachHandler(renderHandler)
    attachHandler(renderJsonHandler)
    pageToHandlers.getOrElseUpdate(page, ArrayBuffer[ServletContextHandler]())
      .append(renderHandler)
  }

  /** Attach a handler to this UI. */
  def attachHandler(handler: ServletContextHandler) {
    handlers += handler
    serverInfo.foreach { info =>
      info.rootHandler.addHandler(handler)
      if (!handler.isStarted) {
        handler.start()
      }
    }
  }

  /** Detach a handler from this UI. */
  def detachHandler(handler: ServletContextHandler) {
    handlers -= handler
    serverInfo.foreach { info =>
      info.rootHandler.removeHandler(handler)
      if (handler.isStarted) {
        handler.stop()
      }
    }
  }

  /**
   * Add a handler for static content.
   *
   * @param resourceBase Root of where to find resources to serve.
   * @param path Path in UI where to mount the resources.
   */
  def addStaticHandler(resourceBase: String, path: String): Unit = {
    attachHandler(MemoJettyUtils.createStaticHandler(resourceBase, path))
  }

  /**
   * Remove a static content handler.
   *
   * @param path Path in UI to unmount.
   */
  def removeStaticHandler(path: String): Unit = {
    handlers.find(_.getContextPath() == path).foreach(detachHandler)
  }

  /** Initialize all components of the server. */
  def initialize()

  /** Bind to the HTTP server behind this web interface. */
  def bind() {
    assert(!serverInfo.isDefined, "Attempted to bind %s more than once!".format(className))
    try {
      serverInfo = Some(startJettyServer("0.0.0.0", port, handlers, conf, name))
      logInfo("Started %s at http://%s:%d".format(className, publicHostName, boundPort))
    } catch {
      case e: Exception =>
        logError("Failed to bind %s".format(className), e)
        System.exit(1)
    }
  }

  /** Return the actual port to which this server is bound. Only valid after bind(). */
  def boundPort: Int = serverInfo.map(_.boundPort).getOrElse(-1)

  /** Stop the server behind this web interface. Only valid after bind(). */
  def stop() {
    assert(serverInfo.isDefined,
      "Attempted to stop %s before binding to a server!".format(className))
    serverInfo.get.server.stop()
  }
}


@SimilarAs(clazz = "WebUITab")
abstract class MemoWebUITab(parent: MemoWebUI, val prefix: String) {
  val pages = ArrayBuffer[MemoWebUIPage]()
  val name = prefix.capitalize

  /** Attach a page to this tab. This prepends the page's prefix with the tab's own prefix. */
  def attachPage(page: MemoWebUIPage) {
    page.prefix = (prefix + "/" + page.prefix).stripSuffix("/")
    pages += page
  }

  /** Get a list of header tabs from the parent UI. */
  def headerTabs: Seq[MemoWebUITab] = parent.getTabs

  def basePath: String = parent.getBasePath
}

@SimilarAs(clazz = "WebUIPage")
abstract class MemoWebUIPage(var prefix: String) {
  def render(request: HttpServletRequest): Seq[Node]
  def renderJson(request: HttpServletRequest): JValue = JNothing
}

