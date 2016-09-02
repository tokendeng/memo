package org.apache.spark.memo.deploy.master

import com.hikvision.bigdata.memo.annotation.{Dependence, SimilarAs}
import org.apache.spark.SparkConf
import org.apache.spark.util.{IntParam, Utils}

/**
 * Created by dengchangchun on 2016/8/17.
 *
 * This is basically a complete copy of MasterArguments. We copy because we want to override some behaviors and make
 * it suitable to MemoMaster.
 *
 * We put this class in org.apache.spark.memo.deploy.master just because of having to use Utils, but Utils is
 * org.apache.spark private.
 */
@Dependence(project="Spark", module="core", clazz="Utils")
@SimilarAs(clazz="MasterArguments")
private[master] class MemoMasterArguments(args: Array[String], conf: SparkConf) {
  var host = Utils.localHostName()
  var port = 7078
  var webUiPort = 8081
  var propertiesFile: String = null

  // Check for settings in environment variables
  if (System.getenv("SPARK_MEMO_MASTER_HOST") != null) {
    host = System.getenv("SPARK_MEMO_MASTER_HOST")
  }
  if (System.getenv("SPARK_MEMO_MASTER_PORT") != null) {
    port = System.getenv("SPARK_MEMO_MASTER_PORT").toInt
  }
  if (System.getenv("SPARK_MEMO_MASTER_WEBUI_PORT") != null) {
    webUiPort = System.getenv("SPARK_MEMO_MASTER_WEBUI_PORT").toInt
  }

  parse(args.toList)

  // This mutates the SparkConf, so all accesses to it must be made after this line
  propertiesFile = Utils.loadDefaultSparkProperties(conf, propertiesFile)

  if (conf.contains("spark.memo.master.ui.port")) {
    webUiPort = conf.get("spark.memo.master.ui.port").toInt
  }

  private def parse(args: List[String]): Unit = args match {
    case ("--ip" | "-i") :: value :: tail =>
      Utils.checkHost(value, "ip no longer supported, please use hostname " + value)
      host = value
      parse(tail)

    case ("--host" | "-h") :: value :: tail =>
      Utils.checkHost(value, "Please use hostname " + value)
      host = value
      parse(tail)

    case ("--port" | "-p") :: IntParam(value) :: tail =>
      port = value
      parse(tail)

    case "--webui-port" :: IntParam(value) :: tail =>
      webUiPort = value
      parse(tail)

    case ("--properties-file") :: value :: tail =>
      propertiesFile = value
      parse(tail)

    case ("--help") :: tail =>
      printUsageAndExit(0)

    case Nil => {}

    case _ =>
      printUsageAndExit(1)
  }

  /**
   * Print usage and exit JVM with the given exit code.
   */
  private def printUsageAndExit(exitCode: Int) {
    // scalastyle:off println
    System.err.println(
      "Usage: Master [options]\n" +
        "\n" +
        "Options:\n" +
        "  -i HOST, --ip HOST     Hostname to listen on (deprecated, please use --host or -h) \n" +
        "  -h HOST, --host HOST   Hostname to listen on\n" +
        "  -p PORT, --port PORT   Port to listen on (default: 7078)\n" +
        "  --webui-port PORT      Port for web UI (default: 8081)\n" +
        "  --properties-file FILE Path to a custom Spark properties file.\n" +
        "                         Default is conf/spark-defaults.conf.")
    // scalastyle:on println
    System.exit(exitCode)
  }
}
