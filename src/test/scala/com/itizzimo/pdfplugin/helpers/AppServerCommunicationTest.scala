package com.itizzimo.pdfplugin.helpers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.itizzimo.pdfplugin.helpers.AppServerCommunication.AssetTimeoutException
import com.typesafe.config.ConfigFactory
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.{AppServer, RequestSource}
import io.simplifier.pluginbase.{PluginDescription, PluginSettings}
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Failure

class AppServerCommunicationTest extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  private implicit lazy val actorSystem: ActorSystem = ActorSystem("appServerCommunicationTest")
  private implicit lazy val materializer: Materializer = Materializer.matFromSystem
  private implicit val requestSource: RequestSource = AppServer(None)

  /** Asset timeout used by the tests, kept short so a timeout case does not slow the suite down. */
  private val AssetTimeout: FiniteDuration = 2.seconds

  private lazy val pluginSettings: PluginSettings =
    PluginSettings(ConfigFactory.parseString(s"plugin.timeoutSeconds = ${AssetTimeout.toSeconds}"), "test-secret")

  override protected def afterAll(): Unit = {
    actorSystem.terminate()
    super.afterAll()
  }

  "getAsset" when {

    "the asset is delivered" should {
      "return its bytes" in {
        val communication = communicationReturning(Source(List(ByteString("PDF-"), ByteString("bytes"))))

        val result = communication.getAsset("logo.png")

        result.map(new String(_, UTF_8)) should be(scala.util.Success("PDF-bytes"))
      }
    }

    "the asset stream never completes" should {
      "fail with an asset timeout and terminate the stream, so the connection is released" in {
        val termination = Promise[Done]()
        val communication = communicationReturning(watched(Source.never[ByteString], termination))

        val started = System.nanoTime()
        val result = communication.getAsset("stalling.png")
        val elapsed = (System.nanoTime() - started).nanos

        inside(result) { exc =>
          exc shouldBe an[AssetTimeoutException]
          exc.getMessage should include("stalling.png")
        }
        // the stream's own timeout must fire, not the blocking backstop
        elapsed should be < (AssetTimeout + AppServerCommunication.AwaitGrace)
        // and the stream must actually be gone rather than left running
        Await.result(termination.future.recover { case _ => Done }, 5.seconds) should be(Done)
      }
    }

    "the response headers already consume the whole timeout" should {
      "discard the stream instead of leaving it unread" in {
        val termination = Promise[Done]()
        val lateStream = akka.pattern.after(AssetTimeout + 500.millis, actorSystem.scheduler) {
          Future.successful(watched(Source.never[ByteString], termination))
        }
        val communication = communicationFor(_ => lateStream)

        val result = communication.getAsset("late.png")

        inside(result) { exc => exc shouldBe an[AssetTimeoutException] }
        Await.result(termination.future.recover { case _ => Done }, 5.seconds) should be(Done)
      }
    }

    "the download fails" should {
      "return the failure" in {
        val communication = communicationFor(_ => Future.failed(new IllegalStateException("404 from AppServer")))

        communication.getAsset("missing.png").failed.get.getMessage should include("404 from AppServer")
      }
    }
  }

  /** Attach a termination observer to a source, so a test can assert the stream was shut down. */
  private def watched(source: Source[ByteString, Any], termination: Promise[Done]): Source[ByteString, NotUsed] =
    source.watchTermination() { (_, done) =>
      termination.completeWith(done)
      NotUsed
    }

  private def communicationReturning(assetStream: Source[ByteString, Any]): AppServerCommunication =
    communicationFor(_ => Future.successful(assetStream))

  private def communicationFor(download: String => Future[Source[ByteString, Any]]): AppServerCommunication = {
    val dispatcher = new AppServerDispatcher(pluginSettings, PluginDescription("test", "test", "1.0", "test", None)) {
      override def downloadAsset(assetId: String)
                                (implicit userSession: UserSession,
                                 requestSource: RequestSource): Future[Source[ByteString, Any]] =
        download(assetId)
    }
    new AppServerCommunication(new KeyValueStoreCommunication(dispatcher, pluginSettings), dispatcher, pluginSettings)
  }

  private def inside(result: scala.util.Try[Array[Byte]])(check: Throwable => Unit): Unit = result match {
    case Failure(exc) => check(exc)
    case other => fail(s"Expected a failure, got $other")
  }
}
