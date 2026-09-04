package com.itizzimo.pdfplugin.helpers

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.itizzimo.pdfplugin.helpers.AppServerCommunication.AssetTimeoutException
import com.typesafe.config.ConfigFactory
import io.simplifier.pluginapi.rest.PluginHeaders.{AppServer, RequestSource}
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import io.simplifier.pluginbase.{PluginDescription, PluginSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Failure

/**
  * The asset timeout against a real akka-http server and a real connection pool.
  *
  * [[AppServerCommunicationTest]] stubs `downloadAsset` with a bare `Source.never`, which is not
  * what production hands to the stream: there the source is a pool-managed response entity, and
  * a stalled one keeps a connection checked out. This suite therefore drives the real
  * `AppServerDispatcher.downloadAsset` against a local server that stalls the response body.
  */
class AppServerCommunicationHttpTest extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  private implicit lazy val actorSystem: ActorSystem = ActorSystem("appServerCommunicationHttpTest",
    ConfigFactory.parseString(
      """akka.http.host-connection-pool.max-connections = 2
        |akka.http.client.idle-timeout = 60s
        |akka.loglevel = WARNING
        |""".stripMargin).withFallback(ConfigFactory.load()))
  private implicit lazy val materializer: Materializer = Materializer.matFromSystem
  private implicit val requestSource: RequestSource = AppServer(None)

  /** Asset timeout used by the tests, kept short so a timeout case does not slow the suite down. */
  private val AssetTimeout: FiniteDuration = 2.seconds

  /** Number of asset request bodies the server has been asked to stall and has not finished. */
  private val stalledBodies = new AtomicInteger(0)

  /** Completes when the server side of a stalled body sees the client go away. */
  @volatile private var lastBodyTermination: Promise[Done] = Promise[Done]()

  private lazy val binding = Await.result(
    Http().bindAndHandleAsync(handle, "127.0.0.1", 0), 10.seconds)

  /** When set, the server holds back the response headers for this long. */
  @volatile private var headerDelay: FiniteDuration = Duration.Zero

  private def handle(request: HttpRequest): Future[HttpResponse] = {
    request.discardEntityBytes()
    if (request.uri.path.toString.startsWith("/api/asset/")) {
      val termination = Promise[Done]()
      lastBodyTermination = termination
      stalledBodies.incrementAndGet()
      // headers go out at once, the body never arrives - the body-hang mode of the slow-asset proxy
      val body = Source.never[ByteString].watchTermination() { (mat, done) =>
        termination.completeWith(done)
        done.onComplete(_ => stalledBodies.decrementAndGet())
        mat
      }
      val response = HttpResponse(entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, body))
      if (headerDelay > Duration.Zero) {
        akka.pattern.after(headerDelay, actorSystem.scheduler)(Future.successful(response))
      } else {
        Future.successful(response)
      }
    } else {
      Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ok")))
    }
  }

  private lazy val pluginSettings: PluginSettings = PluginSettings(
    ConfigFactory.parseString(
      s"""plugin.timeoutSeconds = ${AssetTimeout.toSeconds}
         |plugin.registration.exposedHost = "127.0.0.1"
         |plugin.registration.port = ${binding.localAddress.getPort}
         |""".stripMargin), "test-secret")

  private lazy val dispatcher: AppServerDispatcher =
    new AppServerDispatcher(pluginSettings, PluginDescription("test", "test", "1.0", "test", None))

  private lazy val communication: AppServerCommunication =
    new AppServerCommunication(new KeyValueStoreCommunication(dispatcher, pluginSettings), dispatcher, pluginSettings)

  override protected def afterAll(): Unit = {
    Await.ready(binding.terminate(1.second), 10.seconds)
    Await.ready(actorSystem.terminate(), 10.seconds)
    super.afterAll()
  }

  "getAsset against a real AppServer connection" when {

    "the response body never arrives" should {
      "fail from the stream's own timeout, not from the blocking backstop" in {
        val started = System.nanoTime()
        val result = communication.getAsset("hanging.png")
        val elapsed = (System.nanoTime() - started).nanos

        inside(result) { exc =>
          exc shouldBe an[AssetTimeoutException]
          exc.getMessage should include("hanging.png")
        }
        elapsed should be < (AssetTimeout + AppServerCommunication.AwaitGrace)
      }

      "release the connection, so the pool does not run dry" in {
        val termination = {
          communication.getAsset("hanging-release.png")
          lastBodyTermination
        }
        // the server sees the client cancel: the connection is back in the pool
        Await.result(termination.future.recover { case _ => Done }, 5.seconds) should be(Done)

        // and the pool can still serve further requests - with max-connections = 2, a leaked
        // connection per stalled asset would make the third fetch wait for a slot instead of
        // failing on its own asset timeout
        val started = System.nanoTime()
        (1 to 3).foreach { i =>
          inside(communication.getAsset(s"after-$i.png")) { exc => exc shouldBe an[AssetTimeoutException] }
        }
        val elapsed = (System.nanoTime() - started).nanos
        elapsed should be < (3 * (AssetTimeout + AppServerCommunication.AwaitGrace))
        eventually(stalledBodies.get() should be(0))
      }
    }

    "the response headers never arrive" should {
      "fail on the asset timeout rather than on the blocking backstop" in {
        val (result, elapsed) = waitingForHeaders(AssetTimeout * 5) {
          val started = System.nanoTime()
          val result = communication.getAsset("no-headers.png")
          (result, (System.nanoTime() - started).nanos)
        }

        inside(result) { exc =>
          exc shouldBe an[AssetTimeoutException]
          exc.getMessage should include("no-headers.png")
        }
        elapsed should be < (AssetTimeout + AppServerCommunication.AwaitGrace)
      }
    }
  }

  /** Retry a check for a second: stream cancellation reaches the server side asynchronously. */
  private def eventually(check: => Unit): Unit = {
    val deadline = 5.seconds.fromNow
    var last: Option[Throwable] = None
    while (deadline.hasTimeLeft()) {
      try { check; return } catch { case exc: Throwable => last = Some(exc); Thread.sleep(50) }
    }
    last.foreach(throw _)
  }

  private def waitingForHeaders[A](delay: FiniteDuration)(body: => A): A = {
    headerDelay = delay
    try body finally headerDelay = Duration.Zero
  }

  private def inside(result: scala.util.Try[Array[Byte]])(check: Throwable => Unit): Unit = result match {
    case Failure(exc) => check(exc)
    case other => fail(s"Expected a failure, got $other")
  }
}
