package com.itizzimo.pdfplugin.actor

import io.simplifier.pluginapi.helper.Base64Encoding
import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.{MergeGenerationJob, PdfGenerationJob, SubTaskException}
import com.itizzimo.pdfplugin.actor.StepConvert.ConvertResult
import com.itizzimo.pdfplugin.helpers.{ContentRepoCommunication, KeyValueStoreCommunication}
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource


/**
 * PDF Generation step: Fetch PDF resources to merge to dynamic PDF from AppServer.
 *
 * @author Christian Simon
 */
class StepFetchMergeResources(keyValueStoreCommunication: KeyValueStoreCommunication,
                              contentRepoCommunication: ContentRepoCommunication) extends PdfGenerationActor with Base64Encoding{

  import StepFetchMergeResources._

  override def receive: PartialFunction[Any, Unit] = {
    case ConvertResult(job: PdfGenerationJob, pdfData) => try {
      val PdfGenerationJob(jobId, keyValueStoreId, _, _, _, _, _, _, _, _, _, _, _) = job
      val resourcesToMerge = fetchKeyValueStoreMergeResources(jobId, keyValueStoreId)(job.requestSource, job.userSession)
      sender ! PdfWithMergeResources(job, pdfData, resourcesToMerge)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
    case ConvertResult(job: MergeGenerationJob, pdfData) => try {
      val MergeGenerationJob(jobId, keyValueStoreId, contentRepoIds, _, _, _, _, _, _) = job
      val resourcesToMerge = keyValueStoreId.map(fetchKeyValueStoreMergeResources(jobId, _)(job.requestSource, job.userSession)).getOrElse(Seq()) ++
        fetchContentRepoMergeResources(jobId, contentRepoIds.getOrElse(Seq()))(job.requestSource, job.userSession)
      sender ! PdfWithMergeResources(job, pdfData, resourcesToMerge)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def fetchKeyValueStoreMergeResources(jobId: String, keyValueStoreId: String)
                                 (implicit requestSource: RequestSource, userSession: UserSession): Seq[Array[Byte]] = {
    log.debug(s"[Job $jobId] Fetching merge resources ...")
    keyValueStoreCommunication.getMergeResources(keyValueStoreId) match {
      case None =>
        log.warn("Error getting resources to merge from AppServer - using empty list instead.")
        Seq()
      case Some(data) =>
        log.debug(s"Found ${data.size} resources to merge.")
        data
    }
  }

  private def fetchContentRepoMergeResources(jobId: String, contentRepoIds: Seq[Int])
                                            (implicit requestSource: RequestSource, userSession: UserSession): Seq[Array[Byte]] = {
    log.debug(s"[Job $jobId] Fetching merge resources ...")
    contentRepoIds.map {fileId =>
      decodeB64(contentRepoCommunication.getFileInFolder(fileId)(requestSource, userSession).data)
    }
  }

}

object StepFetchMergeResources {

  /**
    * Result object of the step FetchMergeResources.
    *
    * @param job initial generation job
    * @param pdfData binary data of converted PDF
    * @param mergeResources sequence of the binary data of the merge resources
    */
  case class PdfWithMergeResources(job: PdfJobAbstraction, pdfData: Array[Byte], mergeResources: Seq[Array[Byte]])
}