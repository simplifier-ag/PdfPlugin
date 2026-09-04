package com.itizzimo.pdfplugin.helpers

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

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Try}

/**
  * Reproduction attempt for the deployment finding in
  * documentation/slow-asset-verification.md: the asset stream's `completionTimeout` never fires,
  * and the blocking backstop in `getAsset` wins the race instead.
  *
  * Hypothesis under test: `getAsset` blocks the very dispatcher the stream interpreter runs on,
  * so the timer callback cannot be executed.
  */
class AssetTimeoutStarvationTest extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  private implicit lazy val actorSystem: ActorSystem = ActorSystem("assetTimeoutStarvationTest",
    ConfigFactory.parseString(
      """starved-dispatcher {
        |  type = Dispatcher
        |  executor = "thread-pool-executor"
        |  thread-pool-executor.fixed-pool-size = 1
        |  throughput = 1
        |}
        |akka.loglevel = WARNING
        |""".stripMargin).withFallback(ConfigFactory.load()))

  /** The materializer handed to the AppServerCommunication: one single thread, as a busy plugin. */
  private lazy val starvedMaterializer: Materializer =
    akka.stream.ActorMaterializer(akka.stream.ActorMaterializerSettings(actorSystem).withDispatcher("starved-dispatcher"))

  private implicit lazy val materializer: Materializer = Materializer.matFromSystem
  private implicit val requestSource: RequestSource = AppServer(None)

  private val AssetTimeout: FiniteDuration = 2.seconds

  private lazy val binding = Await.result(Http().bindAndHandleAsync(handle, "127.0.0.1", 0), 30.seconds)

  private def handle(request: HttpRequest): Future[HttpResponse] = {
    request.discardEntityBytes()
    Future.successful(HttpResponse(entity =
      HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, Source.never[ByteString])))
  }

  private lazy val pluginSettings: PluginSettings = PluginSettings(
    ConfigFactory.parseString(
      s"""plugin.timeoutSeconds = ${AssetTimeout.toSeconds}
         |plugin.registration.exposedHost = "127.0.0.1"
         |plugin.registration.port = ${binding.localAddress.getPort}
         |""".stripMargin), "test-secret")

  private lazy val communication: AppServerCommunication = {
    val dispatcher = new AppServerDispatcher(pluginSettings, PluginDescription("test", "test", "1.0", "test", None))
    new AppServerCommunication(new KeyValueStoreCommunication(dispatcher, pluginSettings), dispatcher, pluginSettings)(starvedMaterializer)
  }

  override protected def afterAll(): Unit = {
    Await.ready(binding.terminate(1.second), 30.seconds)
    Await.ready(actorSystem.terminate(), 30.seconds)
    super.afterAll()
  }

  "getAsset called from the dispatcher that also runs the stream" should {
    // Regression test for documentation/slow-asset-verification.md: with the timeout inside the
    // stream (`completionTimeout`) this failed with a plain `Futures timed out after [7 seconds]`
    // from the blocking backstop, because the blocked dispatcher could never run the stage's timer.
    "still fail with an asset timeout, within the asset budget" in {
      // this is what StepFetchAssets does: block an actor thread until the asset is there
      val onActorThread: Future[(Try[Array[Byte]], FiniteDuration)] = Future {
        val started = System.nanoTime()
        val result = communication.getAsset("hanging.png")
        (result, (System.nanoTime() - started).nanos)
      }(actorSystem.dispatchers.lookup("starved-dispatcher"))

      val (result, elapsed) = Await.result(onActorThread, 60.seconds)
      info(s"getAsset returned after ${elapsed.toMillis} ms with $result")

      result match {
        case Failure(exc) => exc shouldBe an[AssetTimeoutException]
        case other => fail(s"Expected a failure, got $other")
      }
      elapsed should be < (AssetTimeout + AppServerCommunication.AwaitGrace)
    }
  }
}
