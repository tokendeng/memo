package org.apache.spark.memo.deploy.container

import java.lang.management.ManagementFactory

import com.hikvision.bigdata.memo.annotation.{SimilarAs, Dependence}
import org.apache.spark.SparkConf
import org.apache.spark.util.{MemoryParam, IntParam, Utils}

/**
 * Created by dengchangchun on 2016/8/18.
 *
 * Command-line parser for the worker.
 */
@Dependence(project="Spark", module="core", clazz="Utils")
@SimilarAs(clazz="WorkerArguments")
private[container] class ContainerArguments(args: Array[String], conf: SparkConf) {
  var host = Utils.localHostName()
  var port = 0
  var webUiPort = 8082
  var memory = inferDefaultMemory()
  var masters: Array[String] = null
  var propertiesFile: String = null

  // Check for settings in environment variables
  if (System.getenv("SPARK_MEMO_CONTAINER_PORT") != null) {
    port = System.getenv("SPARK_MEMO_CONTAINER_PORT").toInt
  }
  if (conf.getenv("SPARK_MEMO_CONTAINER_MEMORY") != null) {
    memory = Utils.memoryStringToMb(conf.getenv("SPARK_MEMO_CONTAINER_MEMORY"))
  }
  if (System.getenv("SPARK_MEMO_CONTAINER_WEBUI_PORT") != null) {
    webUiPort = System.getenv("SPARK_MEMO_CONTAINER_WEBUI_PORT").toInt
  }


  parse(args.toList)

  // This mutates the SparkConf, so all accesses to it must be made after this line
  propertiesFile = Utils.loadDefaultSparkProperties(conf, propertiesFile)

  if (conf.contains("spark.memo.container.ui.port")) {
    webUiPort = conf.get("spark.memo.container.ui.port").toInt
  }

  checkWorkerMemory()

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


    case ("--memory" | "-m") :: MemoryParam(value) :: tail =>
      memory = value
      parse(tail)


    case "--webui-port" :: IntParam(value) :: tail =>
      webUiPort = value
      parse(tail)

    case ("--properties-file") :: value :: tail =>
      propertiesFile = value
      parse(tail)

    case ("--help") :: tail =>
      printUsageAndExit(0)

    case value :: tail =>
      if (masters != null) {  // Two positional arguments were given
        printUsageAndExit(1)
      }
      masters = Utils.parseStandaloneMasterUrls(value)
      parse(tail)

    case Nil =>
      if (masters == null) {  // No positional argument was given
        printUsageAndExit(1)
      }

    case _ =>
      printUsageAndExit(1)
  }

  /**
   * Print usage and exit JVM with the given exit code.
   */
  def printUsageAndExit(exitCode: Int) {
    // scalastyle:off println
    System.err.println(
      "Usage: Container [options] <master>\n" +
        "\n" +
        "Master must be a URL of the form spark://hostname:port\n" +
        "\n" +
        "Options:\n" +
        "  -m MEM, --memory MEM     Amount of memory to use (e.g. 1000M, 2G)\n" +
        "  -i HOST, --ip IP         Hostname to listen on (deprecated, please use --host or -h)\n" +
        "  -h HOST, --host HOST     Hostname to listen on\n" +
        "  -p PORT, --port PORT     Port to listen on (default: random)\n" +
        "  --webui-port PORT        Port for web UI (default: 8081)\n" +
        "  --properties-file FILE   Path to a custom Spark properties file.\n" +
        "                           Default is conf/spark-defaults.conf.")
    // scalastyle:on println
    System.exit(exitCode)
  }

  @SimilarAs( method = "inferDefaultMemory")
  def inferDefaultMemory(): Int = {
    val ibmVendor = System.getProperty("java.vendor").contains("IBM")
    var totalMb = 0
    try {
      // scalastyle:off classforname
      val bean = ManagementFactory.getOperatingSystemMXBean()
      if (ibmVendor) {
        val beanClass = Class.forName("com.ibm.lang.management.OperatingSystemMXBean")
        val method = beanClass.getDeclaredMethod("getTotalPhysicalMemory")
        totalMb = (method.invoke(bean).asInstanceOf[Long] / 1024 / 1024).toInt
      } else {
        val beanClass = Class.forName("com.sun.management.OperatingSystemMXBean")
        val method = beanClass.getDeclaredMethod("getTotalPhysicalMemorySize")
        totalMb = (method.invoke(bean).asInstanceOf[Long] / 1024 / 1024).toInt
      }
      // scalastyle:on classforname
    } catch {
      case e: Exception => {
        totalMb = 2*1024
        // scalastyle:off println
        System.out.println("Failed to get total physical memory. Using " + totalMb + " MB")
        // scalastyle:on println
      }
    }
    // Leave out 1 GB for the operating system, allocate 1/3 of remains to MEMo,
    // but don't return a negative memory size
    math.max((totalMb - 1024)/3, org.apache.spark.memo.deploy.container.DEFAULT_CONTAINER_MEM_MB)
  }

  @SimilarAs( method = "checkWorkerMemory")
  def checkWorkerMemory(): Unit = {
    if (memory <= 0) {
      val message = "Memory can't be 0, missing a M or G on the end of the memory specification?"
      throw new IllegalStateException(message)
    }
  }

}
