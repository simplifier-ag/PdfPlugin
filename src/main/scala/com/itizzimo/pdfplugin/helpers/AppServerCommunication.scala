package com.itizzimo.pdfplugin.helpers

import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{KillSwitches, Materializer, UniqueKillSwitch}
import akka.util.ByteString
import com.typesafe.config.Config
import io.simplifier.pluginapi.helper.{Base64Encoding, PluginLogger}
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import org.json4s._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * Abstraction for Communication with AppServer.
  *
  * @author Christian Simon
  */
class AppServerCommunication(keyValueStoreCommunication: KeyValueStoreCommunication,
                             appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings)
                            (implicit materializer: Materializer) extends Base64Encoding with PluginLogger {

  import AppServerCommunication._

  type BinaryData = Array[Byte]

  private def kvKeyForError(jobId: String) = s"pdf/$jobId.log"

  /** Total budget for fetching one asset: connecting, response headers and reading the body. */
  private def assetTimeout: FiniteDuration = pluginSettings.timeout.duration

  /**
    * Retrieve an asset from the AppServer.
    *
    * @param filename filename of the asset
    * @return the asset data, or a Failure if it could not be fetched within the asset timeout
    */
  def getAsset(filename: String)(implicit requestSource: RequestSource): Try[Array[Byte]] = Try {
    // The fetch owns the timeout (see getAssetData), so this wait is only a backstop for a future
    // that never completes at all - hence the grace period, which keeps the fetch's own failure
    // ahead of this one. Losing that race would leave the response entity unconsumed and its
    // connection checked out of the AppServer connection pool.
    Await.result(getAssetData(filename), assetTimeout + AwaitGrace)
  }

  /**
    * Retrieve an asset from the AppServer without blocking.
    *
    * Connecting, response headers and reading the body share one deadline, enforced by the actor
    * system's scheduler. When it expires the fetch fails with an [[AppServerCommunication.AssetTimeoutException]]
    * and the byte stream is aborted, which releases the AppServer connection back into the pool
    * (default `max-connections = 4`).
    *
    * The deadline deliberately does not live inside the stream: a `completionTimeout` operator can
    * only fail the stream once the stream's own interpreter runs, so a busy or blocked dispatcher
    * postpones it indefinitely - measured against a real deployment, that is exactly what happened,
    * and the blocking backstop in [[getAsset]] won the race while the connection stayed checked out.
    * The scheduler has its own thread and is not affected by that.
    *
    * @param filename filename of the asset
    * @return future of the asset data
    */
  def getAssetData(filename: String)(implicit requestSource: RequestSource): Future[Array[Byte]] = {
    implicit val userSession: UserSession = UserSession.unauthenticated

    val request = AssetRequest(filename)
    val timeout = assetTimeout
    val expiry = Promise[Array[Byte]]()
    // set as soon as the response entity is materialized; before that there is nothing to cancel,
    // and the completed promise makes the late arrival abort itself instead
    val streamCancel = new AtomicReference[Option[UniqueKillSwitch]](None)

    val timer = materializer.system.scheduler.scheduleOnce(timeout) {
      if (expiry.tryFailure(AssetTimeoutException(filename, timeout))) {
        abort(filename, streamCancel.get(), timeout)
      }
    }(SameThread)

    val fetch = appServerDispatcher.downloadAsset(request.name) flatMap { assetStream =>
      val (killSwitch, bytes) = assetStream
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.both)
        .run()
      streamCancel.set(Some(killSwitch))
      // the headers may have used up the whole budget: nothing will read this stream any more
      if (expiry.isCompleted) abort(filename, Some(killSwitch), timeout)
      bytes.map(_.toArray[Byte])
    }

    Future.firstCompletedOf(Seq(fetch, expiry.future))
      .recoverWith {
        case _: TimeoutException => Future.failed(AssetTimeoutException(filename, timeout))
      }
      .transform { result => timer.cancel(); result }
  }

  /**
    * Abort an asset stream whose budget has expired, so that its connection is released immediately
    * instead of being held until the connection pool's idle timeout.
    */
  private def abort(filename: String, killSwitch: Option[UniqueKillSwitch], timeout: FiniteDuration): Unit = {
    killSwitch.foreach { ks =>
      log.debug(s"Aborting asset stream for '$filename': no time left in the asset timeout")
      Try(ks.abort(AssetTimeoutException(filename, timeout))) recover {
        case exc: Exception => log.warn(s"Could not abort asset stream for '$filename'", exc)
      }
    }
  }

  /**
    * Store generation error log in Key-Value-Store.
    *
    * @param jobId PDF generation job ID
    * @param error error message
    */
  def logError(jobId: String, error: String)(implicit requestSource: RequestSource, userSession: UserSession): Unit = {
    val key = kvKeyForError(jobId)

    keyValueStoreCommunication.put(key, error.getBytes(UTF_8)) match {
      case Success(_) =>
      case Failure(exception) => log.error("Error storing error log to KeyValue Store", exception)
    }
    if (error.startsWith("throwMe")) throw new Exception(s"${error.replaceFirst("throwMe:", "")}")
  }

}

object AppServerCommunication extends PluginLogger {

  implicit val formats: Formats = DefaultFormats

  /**
    * Extra time granted to the blocking wait in `getAsset` on top of the asset timeout, so that the
    * fetch's own deadline always fires first and releases the connection.
    */
  final val AwaitGrace: FiniteDuration = 5.seconds

  /**
    * Runs the asset deadline on the scheduler's own thread. Handing it to a dispatcher instead
    * would make the timeout as postponable as the stream operator it replaces; the work it does -
    * failing a promise and aborting a stream - does not block.
    */
  private object SameThread extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()
    override def reportFailure(cause: Throwable): Unit = throw cause
  }

  /** Config keys deciding how quickly a stalled AppServer connection is reclaimed. */
  final val ClientIdleTimeoutPath = "akka.http.client.idle-timeout"
  final val MaxRetriesPath = "akka.http.host-connection-pool.max-retries"
  final val MaxConnectionsPath = "akka.http.host-connection-pool.max-connections"

  /** akka-http's own defaults, which apply to any key the plugin's settings do not set. */
  final val AkkaClientIdleTimeout: FiniteDuration = 60.seconds
  final val AkkaMaxRetries = 5
  final val AkkaMaxConnections = 4

  /**
    * Check the settings that govern the AppServer connection pool against the asset budget, and
    * return one message per problem found.
    *
    * These cannot be clamped the way [[com.itizzimo.pdfplugin.actor.StepConvert.conversionTimeout]]
    * clamps its own value: akka reads them when the actor system is built, which plugin-base does
    * from a config this plugin never sees. Reporting them at startup is therefore the only
    * enforcement available - and worth having, because a `settings.conf` written before these keys
    * existed silently runs on akka's defaults, which is the configuration that stalls the plugin.
    *
    * @param config      the plugin configuration
    * @param assetTimeout budget for one asset fetch, i.e. `plugin.timeoutSeconds`
    */
  def checkConnectionSettings(config: Config, assetTimeout: FiniteDuration): Seq[String] = {
    val idleTimeout = clientIdleTimeout(config)
    val maxRetries = intSetting(config, MaxRetriesPath, AkkaMaxRetries)
    val maxConnections = intSetting(config, MaxConnectionsPath, AkkaMaxConnections)
    val target = s"Set it to ${assetTimeout.toSeconds}s."

    val idleWarnings =
      if (!config.hasPath(ClientIdleTimeoutPath))
        Seq(s"$ClientIdleTimeoutPath is not set, so akka's default of " +
          s"${AkkaClientIdleTimeout.toSeconds}s applies. It is the only bound on an asset whose " +
          s"response headers never arrive, and $maxConnections of those exhaust the pool. $target")
      else idleTimeout match {
        case None =>
          Seq(s"$ClientIdleTimeoutPath is infinite: an AppServer response that never starts holds " +
            s"its connection forever. $target")
        case Some(idle) if looksLikeMissingUnit(config, idle) =>
          Seq(s"$ClientIdleTimeoutPath is ${idle.toMillis}ms - a value without a unit is read as " +
            s"milliseconds, which cuts every AppServer connection almost immediately. " +
            s"Write '${assetTimeout.toSeconds} s'.")
        case Some(idle) if idle > assetTimeout =>
          Seq(s"$ClientIdleTimeoutPath (${idle.toSeconds}s) is longer than the asset budget " +
            s"(${assetTimeout.toSeconds}s): an asset whose response headers never arrive keeps its " +
            s"connection for ${idle.toSeconds}s, and $maxConnections of those exhaust the pool. $target")
        case Some(idle) if idle < assetTimeout =>
          Seq(s"$ClientIdleTimeoutPath (${idle.toSeconds}s) is shorter than the asset budget " +
            s"(${assetTimeout.toSeconds}s): a slow AppServer response is cut before the budget " +
            s"expires. $target")
        case _ => Nil
      }

    val retryWarnings =
      if (maxRetries > 0 && idleTimeout.isDefined)
        Seq(s"$MaxRetriesPath is $maxRetries: a request the idle timeout cuts is re-issued, which " +
          "multiplies load on an AppServer that is by definition already slow. Set it to 0.")
      else Nil

    val poolWarnings =
      if (maxConnections <= AkkaMaxConnections)
        Seq(s"$MaxConnectionsPath is $maxConnections, shared by every AppServer call this plugin " +
          "makes. A template with more images than that serialises its asset downloads.")
      else Nil

    idleWarnings ++ retryWarnings ++ poolWarnings
  }

  /** Run [[checkConnectionSettings]] and log what it found. */
  def logConnectionSettings(config: Config, assetTimeout: FiniteDuration): Unit =
    checkConnectionSettings(config, assetTimeout) foreach { warning =>
      log.warn(s"AppServer connection settings: $warning")
    }

  /** `None` when the idle timeout is infinite, so nothing ever reclaims the connection. */
  private def clientIdleTimeout(config: Config): Option[FiniteDuration] =
    Try(config.getString(ClientIdleTimeoutPath)).toOption match {
      case None => Some(AkkaClientIdleTimeout)
      case Some(InfiniteTimeout()) => None
      case Some(_) =>
        Try(FiniteDuration(config.getDuration(ClientIdleTimeoutPath, MILLISECONDS), MILLISECONDS))
          .toOption.orElse(Some(AkkaClientIdleTimeout))
    }

  /**
    * A bare number is a duration in milliseconds to Typesafe Config, and practically always a
    * missing unit rather than a deliberate sub-second timeout.
    */
  private def looksLikeMissingUnit(config: Config, idleTimeout: FiniteDuration): Boolean =
    idleTimeout < 1.second &&
      Try(config.getString(ClientIdleTimeoutPath)).toOption.exists(_.trim.forall(_.isDigit))

  private def intSetting(config: Config, path: String, default: Int): Int =
    Try(config.getInt(path)).getOrElse(default)

  private object InfiniteTimeout {
    private val values = Set("infinite", "off")
    def unapply(raw: String): Boolean = values.contains(raw.trim.toLowerCase)
  }

  case class AssetRequest(name: String)

  case class AssetResponse(found: Boolean, data: Option[String])

  case class AppServerCommunicationException(msg: String) extends Exception(msg)

  case class AssetTimeoutException(filename: String, timeout: FiniteDuration)
    extends Exception(s"Asset '$filename' could not be fetched within ${timeout.toSeconds}s")

  object ExtractAssetResult extends ExtractJsonResponse[AssetResponse]

  /**
    * Extractor class to extract values from JSON in case statements.
    */
  class ExtractJsonResponse[A](implicit manifest: Manifest[A]) {
    def unapply(json: JValue): Option[A] = Extraction.extractOpt(json)
  }

}