package com.itizzimo.pdfplugin.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.itizzimo.pdfplugin.DocumentConfig
import com.itizzimo.pdfplugin.actor.ProcessGeneration.{DirectGenerationJob, SubTaskException}
import com.itizzimo.pdfplugin.actor.StepConvert.{ConversionTimeoutException, ConvertResult}
import com.typesafe.config.{Config, ConfigFactory}
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.AppServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class StepConvertTest extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  private lazy val actorSystem: ActorSystem = ActorSystem("stepConvertTest")

  override protected def afterAll(): Unit = {
    actorSystem.terminate()
    super.afterAll()
  }

  "StepConvert.conversionTimeout" when {

    "no value is configured" should {
      "derive a timeout below the step timeout" in {
        StepConvert.conversionTimeout(config(), 100.seconds) should be(90.seconds)
      }
    }

    "a shorter value is configured" should {
      "use the configured value" in {
        StepConvert.conversionTimeout(config("conversionTimeoutSeconds" -> 30), 100.seconds) should be(30.seconds)
      }
    }

    "a value that is not shorter than the step timeout is configured" should {
      "clamp it, because the ask would give up before the process is destroyed" in {
        StepConvert.conversionTimeout(config("conversionTimeoutSeconds" -> 100), 100.seconds) should be(90.seconds)
        StepConvert.conversionTimeout(config("conversionTimeoutSeconds" -> 600), 100.seconds) should be(90.seconds)
      }
    }

    "a non-positive value is configured" should {
      "fall back to the derived timeout" in {
        StepConvert.conversionTimeout(config("conversionTimeoutSeconds" -> 0), 100.seconds) should be(90.seconds)
      }
    }
  }

  "StepConvert" when {

    "the conversion tool does not terminate" should {
      "destroy it and fail the step instead of blocking forever" in {
        val hangingTool = executable("hanging-wkhtmltopdf", "#!/bin/sh\nsleep 600\n")
        val step = actorSystem.actorOf(Props(new StepConvert(config("wkhtmltopdf" -> hangingTool.toString), 1.second)))

        askStep(step, directGenerationJob) match {
          case SubTaskException(exc: ConversionTimeoutException) =>
            exc.getMessage should include("did not finish within 1s")
          case other => fail(s"Expected a conversion timeout, got $other")
        }
        runningProcesses(hangingTool) should be(empty)
      }
    }

    "the conversion tool writes to STDOUT" should {
      "return its output as the PDF data" in {
        val tool = executable("echoing-wkhtmltopdf", "#!/bin/sh\ncat > /dev/null\nprintf '%%PDF-1.4 fake'\n")
        val step = actorSystem.actorOf(Props(new StepConvert(config("wkhtmltopdf" -> tool.toString), 10.seconds)))

        askStep(step, directGenerationJob) match {
          case ConvertResult(_, pdfData) => new String(pdfData, UTF_8) should be("%PDF-1.4 fake")
          case other => fail(s"Expected a conversion result, got $other")
        }
      }
    }
  }

  /** Send a message to a step actor and wait for its single reply. */
  private def askStep(step: ActorRef, message: Any): Any = {
    val reply = Promise[Any]()
    val collector = actorSystem.actorOf(Props(new Actor {
      override def receive: Receive = {
        case msg =>
          reply.trySuccess(msg)
          context stop self
      }
    }))
    step.tell(message, collector)
    Await.result(reply.future, 30.seconds)
  }

  private def directGenerationJob: DirectGenerationJob =
    DirectGenerationJob("test-job", "<html><body>hello</body></html>",
      DocumentConfig.getPdfConfigFromJSON(None, allowJavascript = false), AppServer(None),
      UserSession.unauthenticated, hasCustomHeader = false, hasCustomFooter = false)

  private def config(settings: (String, Any)*): Config =
    ConfigFactory.parseMap(Map[String, Any](
      "pdfPlugin.wkhtmltopdf" -> "/bin/true",
      "pdfPlugin.clearTempFiles" -> false
    ).++(settings.map { case (key, value) => s"pdfPlugin.$key" -> value }).asJava)

  /** Write a shell script to a temp file and make it executable. */
  private def executable(name: String, script: String): Path = {
    val file = Files.createTempFile(name, ".sh")
    Files.write(file, script.getBytes(UTF_8))
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
    file.toFile.deleteOnExit()
    file
  }

  private def runningProcesses(executable: Path): Seq[String] = {
    val process = new ProcessBuilder("pgrep", "-f", executable.getFileName.toString)
      .redirectErrorStream(true).start()
    val output = scala.io.Source.fromInputStream(process.getInputStream).mkString
    process.waitFor()
    output.linesIterator.filter(_.trim.nonEmpty).toSeq
  }
}
