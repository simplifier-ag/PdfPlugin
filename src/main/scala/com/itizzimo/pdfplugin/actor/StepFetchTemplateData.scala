package com.itizzimo.pdfplugin.actor

import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfGenerationJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.{PdfGenerationJob, SubTaskException}
import com.itizzimo.pdfplugin.helpers.KeyValueStoreCommunication
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource


/**
 * PDF Generation step: Fetch template data from AppServer.
 *
 * @author Christian Simon
 */
class StepFetchTemplateData(keyValueStoreCommunication: KeyValueStoreCommunication) extends PdfGenerationActor {

  import StepFetchTemplateData._

  override def receive: PartialFunction[Any, Unit] = {
    case job: PdfGenerationJobAbstraction => try {
      val PdfGenerationJob(jobId, keyValueStoreId, _, _, _, _, _, _, _, _, _, _, _) = job

      val dataMap = fetchTemplateData(jobId, keyValueStoreId)(job.requestSource, job.userSession)
      sender ! TemplateData(job, dataMap)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def fetchTemplateData(jobId: String, session: String)
                               (implicit requestSource: RequestSource, userSession: UserSession): Map[String, Any] = {
    log.info(s"[Job $jobId] Fetching PDF data ...")

    getDataMap(session)
  }

  private def getDataMap(session: String)(implicit requestSource: RequestSource, userSession: UserSession): Map[String, Any] = {
    keyValueStoreCommunication.getSessionData(session) match {
      case None =>
        log.warn("Error getting session data from AppServer - using empty map instead.")
        Map[String, Any]()
      // TODO: Throw error?
      case Some(data) =>
        log.debug(s"Found session data with ${data.size} properties.")
        data
    }
  }

}

object StepFetchTemplateData {

  /**
    * Result object of the step FetchTemplateData.
    *
    * @param job initial generation job
    * @param data template data
    */
  case class TemplateData(job: PdfGenerationJobAbstraction, data: Map[String, Any])
}