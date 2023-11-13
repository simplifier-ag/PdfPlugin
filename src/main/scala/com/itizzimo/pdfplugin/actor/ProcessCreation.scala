package com.itizzimo.pdfplugin.actor

import java.util.concurrent.TimeUnit._

import akka.actor.{ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import com.itizzimo.pdfplugin.actor.ProcessGeneration._
import com.itizzimo.pdfplugin.actor.StepCreatePdfWithContent.CreatedPdf
import com.itizzimo.pdfplugin.actor.StepPublishFinishedPdf.PublishingResult
import com.itizzimo.pdfplugin.helpers.{AppServerCommunication, ContentRepoCommunication, KeyValueStoreCommunication}
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.github.simplifier_ag.scala.spdf.PdfConfig

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


/**
  * Created by b001 on 08.08.16.
  * 08.08.2016 10:06
  */
class ProcessCreation(keyValueStoreCommunication: KeyValueStoreCommunication,
                      contentRepoCommunication: ContentRepoCommunication,
                      appServerCommunication: AppServerCommunication) extends PdfGenerationActor {

  import ProcessCreation._

  /** Timeout for each sub-task. */
  implicit val timeout: Timeout = Timeout(1, MINUTES)

  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher

  var originalSender: ActorRef = context.system.deadLetters

  /*
   * Actors for sub-tasks.
   */

  val stepCreatePdfWithContent: ActorRef = context.actorOf(
    Props(new StepCreatePdfWithContent(appServerCommunication)), "createPdfWithContent")
  val stepPublish: ActorRef = context.actorOf(
    Props(new StepPublishFinishedPdf(keyValueStoreCommunication, contentRepoCommunication)), "publishFinishedPdf")

  override def receive: PartialFunction[Any, Unit] = {
    case job: PdfCreationJob =>
      val originalSender = sender()
      val jobId = job.jobId
      log.info(s"[Job $jobId] Starting job ...")
      val process = for {
        createdPdf <- createPdf(job)
        publishResult <- publishFinishedPdf(createdPdf)
      } yield publishResult
      process andThen {
        case Success(result) => log.info(s"[Job $jobId] finished successfully.")
          originalSender ! result
        case Failure(exception) => onFailure(job, exception)
          originalSender ! exception
      }
  }

  private def createPdf(job: PdfCreationJob): Future[CreatedPdf] = {
    val jobId = job.jobId
    (stepCreatePdfWithContent ? job) map {
      case expected: CreatedPdf => expected
      case error: SubTaskException =>
        log.error(s"[Job $jobId] Unexpected error", error.exc)
        throw ProcessErrorPublish("Unexpected error in step CreatePdfWithContent: " + error.exc.getMessage)
      case other =>
        log.warn(s"[Job $jobId] Unexpected result: $other")
        throw ProcessErrorPublish("Unexpected result in step CreatePdfWithContent")
    }
  }

  /** Subtask: Publish finished PDF. */
  private def publishFinishedPdf(mergedPdf: CreatedPdf): Future[PublishingResult] = {
    val jobId = mergedPdf.job.jobId
    (stepPublish ? mergedPdf) map {
      case expected: PublishingResult => expected
      case error: SubTaskException =>
        log.error(s"[Job $jobId] Unexpected error", error.exc)
        throw ProcessErrorPublish("Unexpected error in step PublishFinishedPdf: " + error.exc.getMessage)
      case other =>
        log.warn(s"[Job $jobId] Unexpected result: $other")
        throw ProcessErrorPublish("Unexpected result in step PublishFinishedPdf")
    }
  }

  /**
    * Error handler for failed generations.
    *
    * @param job       generation job that was executed
    * @param exception exception that was caught
    */
  private def onFailure(job: PdfCreationJob, exception: Throwable): Unit = {
    val jobId = job.jobId
    val error = exception match {
      case ProcessException(step, msg) => s"Error during $step: $msg"
      case other => other.toString
    }
    log.error(s"[Job $jobId] Processing Error: $error")
    appServerCommunication.logError(jobId, s"throwMe:$error")(job.requestSource, job.userSession)
  }

}

object ProcessCreation {

  trait PdfJobAbstraction {
    val jobId: String
    val pdfConfig: PdfConfig
    val userSession: UserSession
    val requestSource: RequestSource
    val hasCustomHeader: Boolean
    val hasCustomFooter: Boolean
  }

  trait PdfGenerationJobAbstraction extends PdfJobAbstraction {
    val jobId: String
    val templateName: String
    val templateData: Array[Byte]
    val stylesheetData: Array[Byte]
    val headerData: Option[Array[Byte]]
    val footerData: Option[Array[Byte]]
    val pdfConfig: PdfConfig
    val userSession: UserSession
    val requestSource: RequestSource
    val hasCustomHeader: Boolean
    val hasCustomFooter: Boolean
  }

  /**
    * PDF Creation Job without template.
    *
    * @param jobId       unique ID of job
    * @param sessions    All Session Ids with PDF-Content
    * @param pdfConfig   config for PDF generation
    * @param userSession user session
    * @param requestSource the original request source
    */
  case class PdfCreationJob(jobId: String,
                            sessions: Seq[String],
                            pdfConfig: PdfConfig,
                            userSession: UserSession,
                            requestSource: RequestSource,
                            hasCustomHeader: Boolean,
                            hasCustomFooter: Boolean) extends PdfJobAbstraction

}