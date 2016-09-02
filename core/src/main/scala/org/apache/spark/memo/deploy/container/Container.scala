package org.apache.spark.memo.deploy.container

import java.text.SimpleDateFormat
import java.util.{UUID, Date}
import java.util.concurrent._
import java.util.concurrent.{Future => JFuture, ScheduledFuture => JScheduledFuture}

import com.hikvision.bigdata.memo.annotation.Dependence
import org.apache.spark.memo.deploy.DeployMessages._
import org.apache.spark.memo.deploy.container.ui.ContainerWebUI
import org.apache.spark.memo.deploy.master.MemoMaster
import org.apache.spark.rpc._
import org.apache.spark.{SecurityManager, SparkConf, Logging}
import org.apache.spark.util.{ThreadUtils, Utils, SignalLogger}

import scala.util.Random
import scala.util.control.NonFatal

/**
 * Created by dengchangchun on 2016/8/18.
 */

private[deploy] class Container(
                              override val rpcEnv: RpcEnv,
                              webUiPort: Int,
                              memory: Int,
                              masterRpcAddresses: Array[RpcAddress],
                              systemName: String,
                              endpointName: String,
                              val conf: SparkConf,
                              val securityMgr: SecurityManager)
  extends ThreadSafeRpcEndpoint with Logging {

  private val host = rpcEnv.address.host
  private val port = rpcEnv.address.port

  Utils.checkHost(host, "Expected hostname")
  assert (port > 0)

  // A scheduled executor used to send messages at the specified time.
  private val forwordMessageScheduler =
    ThreadUtils.newDaemonSingleThreadScheduledExecutor("worker-forward-message-scheduler")

  private var master: Option[RpcEndpointRef] = None
  private var activeMasterUrl: String = ""
  private[container] var activeMasterWebUiUrl : String = ""

  private var registered = false
  private var connected = false
  private val containerId = generateContainerId()

  private val publicAddress = {
    val envVar = conf.getenv("SPARK_PUBLIC_DNS")
    if (envVar != null) envVar else host
  }

  // Model retries to connect to the master, after Hadoop's model.
  // The first six attempts to reconnect are in shorter intervals (between 5 and 15 seconds)
  // Afterwards, the next 10 attempts are between 30 and 90 seconds.
  // A bit of randomness is introduced so that not all of the Containers attempt to reconnect at
  // the same time.
  private val INITIAL_REGISTRATION_RETRIES = 6
  private val TOTAL_REGISTRATION_RETRIES = INITIAL_REGISTRATION_RETRIES + 10
  private val FUZZ_MULTIPLIER_INTERVAL_LOWER_BOUND = 0.500
  private val REGISTRATION_RETRY_FUZZ_MULTIPLIER = {
    val randomNumberGenerator = new Random(UUID.randomUUID.getMostSignificantBits)
    randomNumberGenerator.nextDouble + FUZZ_MULTIPLIER_INTERVAL_LOWER_BOUND
  }
  private val INITIAL_REGISTRATION_RETRY_INTERVAL_SECONDS = (math.round(10 *
    REGISTRATION_RETRY_FUZZ_MULTIPLIER))
  private val PROLONGED_REGISTRATION_RETRY_INTERVAL_SECONDS = (math.round(60
    * REGISTRATION_RETRY_FUZZ_MULTIPLIER))

  // For Container IDs
  private def createDateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
  // Send a heartbeat every (heartbeat timeout) / 4 milliseconds
  private val HEARTBEAT_MILLIS = conf.getLong("spark.memo.container.timeout", 60) * 1000 / 4

  private var registerMasterFutures: Array[JFuture[_]] = null
  private var registrationRetryTimer: Option[JScheduledFuture[_]] = None

  // A thread pool for registering with masters. Because registering with a master is a blocking
  // action, this thread pool must be able to create "masterRpcAddresses.size" threads at the same
  // time so that we can register with all masters.
  private val registerMasterThreadPool = new ThreadPoolExecutor(
    0,
    masterRpcAddresses.size, // Make sure we can register with all masters at the same time
    60L, TimeUnit.SECONDS,
    new SynchronousQueue[Runnable](),
    ThreadUtils.namedThreadFactory("container-register-master-threadpool"))

  private var webUi: ContainerWebUI = null

  private var connectionAttemptCount = 0

  var memoryUsed = 0
  def memoryFree: Int = memory - memoryUsed


  /**
   * Send a message to the current master. If we have not yet registered successfully with any
   * master, the message will be dropped.
   */
  private def sendToMaster(message: Any): Unit = {
    master match {
      case Some(masterRef) => masterRef.send(message)
      case None =>
        logWarning(
          s"Dropping $message because the connection to master has not yet been established")
    }
  }

  private def generateContainerId(): String = {
    "container-%s-%s-%d".format(createDateFormat.format(new Date), host, port)
  }

  private def tryRegisterAllMasters(): Array[JFuture[_]] = {
    masterRpcAddresses.map { masterAddress =>
      registerMasterThreadPool.submit(new Runnable {
        override def run(): Unit = {
          try {
            logInfo("Connecting to master " + masterAddress + "...")
            val masterEndpoint =
              rpcEnv.setupEndpointRef(MemoMaster.SYSTEM_NAME, masterAddress, MemoMaster.ENDPOINT_NAME)
            masterEndpoint.send(RegisterContainer(
              containerId, host, port, self, memory, webUi.boundPort , publicAddress))
          } catch {
            case ie: InterruptedException => // Cancelled
            case NonFatal(e) => logWarning(s"Failed to connect to master $masterAddress", e)
          }
        }
      })
    }
  }

  private def registerWithMaster() {
    // onDisconnected may be triggered multiple times, so don't attempt registration
    // if there are outstanding registration attempts scheduled.
    registrationRetryTimer match {
      case None =>
        registered = false
        registerMasterFutures = tryRegisterAllMasters()
        connectionAttemptCount = 0
        registrationRetryTimer = Some(forwordMessageScheduler.scheduleAtFixedRate(
          new Runnable {
            override def run(): Unit = Utils.tryLogNonFatalError {
              self.send(ReregisterWithMaster)
            }
          },
          INITIAL_REGISTRATION_RETRY_INTERVAL_SECONDS,
          INITIAL_REGISTRATION_RETRY_INTERVAL_SECONDS,
          TimeUnit.SECONDS))
      case Some(_) =>
        logInfo("Not spawning another attempt to register with the master, since there is an" +
          " attempt scheduled already.")
    }
  }

  /**
   * Cancel last registeration retry, or do nothing if no retry
   */
  private def cancelLastRegistrationRetry(): Unit = {
    if (registerMasterFutures != null) {
      registerMasterFutures.foreach(_.cancel(true))
      registerMasterFutures = null
    }
    registrationRetryTimer.foreach(_.cancel(true))
    registrationRetryTimer = None
  }

  /**
   * Re-register with the master because a network failure or a master failure has occurred.
   * If the re-registration attempt threshold is exceeded, the container exits with error.
   * Note that for thread-safety this should only be called from the rpcEndpoint.
   */
  private def reregisterWithMaster(): Unit = {
    Utils.tryOrExit {
      connectionAttemptCount += 1
      if (registered) {
        cancelLastRegistrationRetry()
      } else if (connectionAttemptCount <= TOTAL_REGISTRATION_RETRIES) {
        logInfo(s"Retrying connection to master (attempt # $connectionAttemptCount)")
        /**
         * Re-register with the active master this container has been communicating with. If there
         * is none, then it means this container is still bootstrapping and hasn't established a
         * connection with a master yet, in which case we should re-register with all masters.
         *
         * It is important to re-register only with the active master during failures. Otherwise,
         * if the container unconditionally attempts to re-register with all masters, the following
         * race condition may arise and cause a "duplicate worker" error detailed in SPARK-4592:
         *
         *   (1) Master A fails and Worker attempts to reconnect to all masters
         *   (2) Master B takes over and notifies Worker
         *   (3) Container responds by registering with Master B
         *   (4) Meanwhile, Container's previous reconnection attempt reaches Master B,
         *       causing the same Container to register with Master B twice
         *
         * Instead, if we only register with the known active master, we can assume that the
         * old master must have died because another master has taken over. Note that this is
         * still not safe if the old master recovers within this interval, but this is a much
         * less likely scenario.
         */
        master match {
          case Some(masterRef) =>
            // registered == false && master != None means we lost the connection to master, so
            // masterRef cannot be used and we need to recreate it again. Note: we must not set
            // master to None due to the above comments.
            if (registerMasterFutures != null) {
              registerMasterFutures.foreach(_.cancel(true))
            }
            val masterAddress = masterRef.address
            registerMasterFutures = Array(registerMasterThreadPool.submit(new Runnable {
              override def run(): Unit = {
                try {
                  logInfo("Connecting to master " + masterAddress + "...")
                  val masterEndpoint =
                    rpcEnv.setupEndpointRef(MemoMaster.SYSTEM_NAME, masterAddress, MemoMaster.ENDPOINT_NAME)
                  masterEndpoint.send(RegisterContainer(
                    containerId, host, port, self, memory, webUi.boundPort, publicAddress))
                } catch {
                  case ie: InterruptedException => // Cancelled
                  case NonFatal(e) => logWarning(s"Failed to connect to master $masterAddress", e)
                }
              }
            }))
          case None =>
            if (registerMasterFutures != null) {
              registerMasterFutures.foreach(_.cancel(true))
            }
            // We are retrying the initial registration
            registerMasterFutures = tryRegisterAllMasters()
        }
        // We have exceeded the initial registration retry threshold
        // All retries from now on should use a higher interval
        if (connectionAttemptCount == INITIAL_REGISTRATION_RETRIES) {
          registrationRetryTimer.foreach(_.cancel(true))
          registrationRetryTimer = Some(
            forwordMessageScheduler.scheduleAtFixedRate(new Runnable {
              override def run(): Unit = Utils.tryLogNonFatalError {
                self.send(ReregisterWithMaster)
              }
            }, PROLONGED_REGISTRATION_RETRY_INTERVAL_SECONDS,
              PROLONGED_REGISTRATION_RETRY_INTERVAL_SECONDS,
              TimeUnit.SECONDS))
        }
      } else {
        logError("All masters are unresponsive! Giving up.")
        System.exit(1)
      }
    }
  }

  private def changeMaster(masterRef: RpcEndpointRef, uiUrl: String) {
    // activeMasterUrl it's a valid Spark url since we receive it from master.
    activeMasterUrl = masterRef.address.toSparkURL
    activeMasterWebUiUrl = uiUrl
    master = Some(masterRef)
    connected = true
    // Cancel any outstanding re-registration attempts because we found a new master
    cancelLastRegistrationRetry()
  }

  private def masterDisconnected() {
    logError("Connection to master failed! Waiting for master to reconnect...")
    connected = false
    registerWithMaster()
  }

  override def onStart() {
    assert(!registered)
    logInfo("Starting Memo container %s:%d with %s RAM".format(
      host, port, Utils.megabytesToString(memory)))
    logInfo(s"Running Memo version ${org.apache.spark.memo.MEMO_VERSION}")
    webUi = new ContainerWebUI(this, webUiPort)
    webUi.bind()
    registerWithMaster()
  }

  override def receive: PartialFunction[Any, Unit] = {
    case RegisteredContainer(masterRef, masterWebUiUrl) =>
      logInfo("Successfully registered with master " + masterRef.address.toSparkURL)
      registered = true
      changeMaster(masterRef, masterWebUiUrl)
      forwordMessageScheduler.scheduleAtFixedRate(new Runnable {
        override def run(): Unit = Utils.tryLogNonFatalError {
          self.send(SendHeartbeat)
        }
      }, 0, HEARTBEAT_MILLIS, TimeUnit.MILLISECONDS)
      //TODO
      /*
      if (CLEANUP_ENABLED) {
        logInfo(s"Worker cleanup enabled; old application directories will be deleted in: $workDir")
        forwordMessageScheduler.scheduleAtFixedRate(new Runnable {
          override def run(): Unit = Utils.tryLogNonFatalError {
            self.send(WorkDirCleanup)
          }
        }, CLEANUP_INTERVAL_MILLIS, CLEANUP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
      }
      */

    case SendHeartbeat =>
      if (connected) { sendToMaster(Heartbeat(containerId, self)) }

    case RegisterContainerFailed(message) =>
      if (!registered) {
        logError("Container registration failed: " + message)
        System.exit(1)
      }

    case MasterChanged(masterRef, masterWebUiUrl) =>
      logInfo("Master has changed, new master is at " + masterRef.address.toSparkURL)
      changeMaster(masterRef, masterWebUiUrl)

    case ReconnectContainer(masterUrl) =>
      logInfo(s"Master with url $masterUrl requested this container to reconnect.")
      registerWithMaster()

    case ReregisterWithMaster =>
      reregisterWithMaster()
  }

  override def receiveAndReply(context: RpcCallContext): PartialFunction[Any, Unit] = {
    case RequestContainerState =>
      context.reply(ContainerStateResponse(host, port, containerId,  activeMasterUrl,
        memory, memoryUsed, activeMasterWebUiUrl))
  }

  override def onDisconnected(remoteAddress: RpcAddress): Unit = {
    if (master.exists(_.address == remoteAddress)) {
      logInfo(s"$remoteAddress Disassociated !")
      masterDisconnected()
    }
  }

  override def onStop() {
    cancelLastRegistrationRetry()
    forwordMessageScheduler.shutdownNow()
    registerMasterThreadPool.shutdownNow()
    webUi.stop()
  }

}

@Dependence(project="Spark", module="core", clazz="SparkConf")
private[deploy] object Container  extends Logging{
  val SYSTEM_NAME = "memoContainer"
  val ENDPOINT_NAME = "Container"

  def main(argStrings: Array[String]) {
    SignalLogger.register(log)
    val conf = new SparkConf
    val args = new ContainerArguments(argStrings, conf)
    val rpcEnv = startRpcEnvAndEndpoint(args.host, args.port, args.webUiPort,
      args.memory, args.masters)
    rpcEnv.awaitTermination()
  }

  def startRpcEnvAndEndpoint(
                              host: String,
                              port: Int,
                              webUiPort: Int,
                              memory: Int,
                              masterUrls: Array[String],
                              workerNumber: Option[Int] = None,
                              conf: SparkConf = new SparkConf): RpcEnv = {

    // The LocalSparkCluster runs multiple local sparkWorkerX RPC Environments
    val systemName = SYSTEM_NAME + workerNumber.map(_.toString).getOrElse("")
    val securityMgr = new SecurityManager(conf)
    val rpcEnv = RpcEnv.create(systemName, host, port, conf, securityMgr)
    val masterAddresses = masterUrls.map(RpcAddress.fromSparkURL(_))
    rpcEnv.setupEndpoint(ENDPOINT_NAME, new Container(rpcEnv, webUiPort, memory,
      masterAddresses, systemName, ENDPOINT_NAME, conf, securityMgr))
    rpcEnv
  }

}
