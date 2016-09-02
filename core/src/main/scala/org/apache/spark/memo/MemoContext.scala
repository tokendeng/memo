package org.apache.spark.memo

import com.hikvision.bigdata.memo.annotation.SimilarAs
import org.apache.spark.{Logging, SparkConf}

/**
 * Created by dengchangchun on 2016/8/18.
 */
class MemoContext(config: SparkConf) extends Logging {

}

/**
 * A collection of regexes for extracting information from the master string.
 */
@SimilarAs( clazz ="SparkMasterRegex")
private object MemoMasterRegex {
  // Regular expression used for local[N] and local[*] master formats
  // val LOCAL_N_REGEX = """local\[([0-9]+|\*)\]""".r

  // Regular expression for local[N, maxRetries], used in tests with failing tasks
  // val LOCAL_N_FAILURES_REGEX = """local\[([0-9]+|\*)\s*,\s*([0-9]+)\]""".r

  // Regular expression for simulating a Spark cluster of [N, cores, memory] locally
  // val LOCAL_CLUSTER_REGEX = """local-cluster\[\s*([0-9]+)\s*,\s*([0-9]+)\s*,\s*([0-9]+)\s*]""".r

  // Regular expression for simulating a Spark cluster of [N, memory] locally
  val LOCAL_CLUSTER_REGEX = """local-cluster\[\s*([0-9]+)\s*,\s*([0-9]+)\s*]""".r

  // Regular expression for connecting to Spark deploy clusters
  val SPARK_REGEX = """spark://(.*)""".r

  // Regular expression for connection to Mesos cluster by mesos:// or zk:// url
  // val MESOS_REGEX = """(mesos|zk)://.*""".r

  // Regular expression for connection to Simr cluster
  // val SIMR_REGEX = """simr://(.*)""".r
}
