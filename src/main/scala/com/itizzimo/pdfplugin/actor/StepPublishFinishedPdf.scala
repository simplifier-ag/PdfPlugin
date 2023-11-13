package com.itizzimo.pdfplugin.actor

import akka.actor.actorRef2Scala
import com.itizzimo.pdfplugin.actor.ProcessCreation.{PdfCreationJob, PdfJobAbstraction}
import com.itizzimo.pdfplugin.actor.ProcessGeneration.{MergeGenerationJob, PdfGenerationJob, SubTaskException}
import com.itizzimo.pdfplugin.actor.StepCreatePdfWithContent.CreatedPdf
import com.itizzimo.pdfplugin.actor.StepMerge.MergedPdf
import com.itizzimo.pdfplugin.controller.GenerationController.ContentFileInfo
import com.itizzimo.pdfplugin.helpers.{ContentRepoCommunication, KeyValueStoreCommunication}
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource


/**
 * PDF Generation step: send the finished PDF to the AppServer.
  *
  * @author Christian Simon
 */
class StepPublishFinishedPdf(keyValueStoreCommunication: KeyValueStoreCommunication,
                             contentRepoCommunication: ContentRepoCommunication) extends PdfGenerationActor {

  import StepPublishFinishedPdf._

  override def receive: PartialFunction[Any, Unit] = {
    case MergedPdf(job: PdfGenerationJob, data) if job.contentFileInfo.isEmpty => try {
      val PdfGenerationJob(jobId, _, _, _, _, _, userSession, requestSource, _, _, _, _, None) = job
      publishToKVStore(jobId, data, "Publishing finished PDF")(requestSource, userSession)
      sender ! PublishingResult(job)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
    case MergedPdf(job: PdfGenerationJob, data) => try {
      val PdfGenerationJob(_, _, _, _, _, _, _, _, _, _, _, _, Some(contentFileInfo)) = job
      val updatedFileInfo = publishToContentRepo(job, contentFileInfo, data)
      val updatedJob = job.copy(contentFileInfo = Some(updatedFileInfo))
      sender ! PublishingResult(updatedJob)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
    case MergedPdf(job: MergeGenerationJob, data) => try {
      val MergeGenerationJob(_, _, _, _, _, _, _, _, Some(contentFileInfo)) = job
      val updatedFileInfo = publishToContentRepo(job, contentFileInfo, data)
      val updatedJob = job.copy(contentFileInfo = Some(updatedFileInfo))
      sender ! PublishingResult(updatedJob)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
    case CreatedPdf(job, data) => try {
      val PdfCreationJob(jobId, _, _, userSession, requestSource, _, _) = job
      publishToKVStore(jobId, data, "Publishing finished PDF from Created PDF")(requestSource, userSession)
      sender ! PublishingResult(job)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def publishToKVStore(jobId: String, data: Array[Byte], message: String)
                              (implicit requestSource: RequestSource, userSession: UserSession): Unit = {
    log.debug(s"[Job $jobId] $message ...")
    keyValueStoreCommunication.storeToKvStore(jobId, data)
  }

  private def publishToContentRepo(job: PdfJobAbstraction, contentFileInfo: ContentFileInfo, data: Array[Byte]): ContentFileInfo  = {
    log.debug(s"[Job ${job.jobId}] Publishing finished PDF to content repo...")
    val fileId = contentRepoCommunication.storeFinishedPdf(contentFileInfo, data)(job.requestSource, job.userSession)
    contentFileInfo.copy(fileId = Some(fileId))
  }

}

object StepPublishFinishedPdf {

  /**
    * Result object of the step PublishFinishedPdf.
    *
    * @param job initial generation job
    */
  case class PublishingResult(job: PdfJobAbstraction)
}