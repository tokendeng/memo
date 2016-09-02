package org.apache.spark.memo.deploy

import org.apache.spark.SparkConf
import org.scalatest.FunSuite


/**
 * Created by dengchangchun on 2016/8/18.
 */
class LocalMemoClusterSuite extends FunSuite{

  test("test start"){
    val conf = new SparkConf()

    val localCluster = new LocalMemoCluster(3,512,conf)
    localCluster.start()

    localCluster.stop()
  }
}
