package com.itizzimo.pdfplugin.actor

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.itizzimo.pdfplugin.actor.ProcessCreation.{PdfGenerationJobAbstraction, PdfJobAbstraction}
import com.itizzimo.pdfplugin.actor.StepConvert.ConvertResult
import com.itizzimo.pdfplugin.actor.StepEvaluateData.EvaluatedTemplate
import com.itizzimo.pdfplugin.actor.StepFetchAssets.TemplateWithAssets
import com.itizzimo.pdfplugin.actor.StepFetchMergeResources.PdfWithMergeResources
import com.itizzimo.pdfplugin.actor.StepFetchTemplateData.TemplateData
import com.itizzimo.pdfplugin.actor.StepMerge.MergedPdf
import com.itizzimo.pdfplugin.actor.StepPrepareFiles.PreparationResult
import com.itizzimo.pdfplugin.actor.StepPublishFinishedPdf.PublishingResult
import com.itizzimo.pdfplugin.controller.GenerationController.ContentFileInfo
import com.itizzimo.pdfplugin.helpers.{AppServerCommunication, ContentRepoCommunication, FileSystemHelper, KeyValueStoreCommunication}
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import com.typesafe.config.Config
import io.github.simplifier_ag.scala.spdf.PdfConfig
import org.apache.pdfbox.pdmodel.PDDocument

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit.HOURS
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Actor to start and supervise PDF generation.
  *
  * @author Christian Simon
  */
class ProcessGeneration(keyValueStoreCommunication: KeyValueStoreCommunication,
                        contentRepoCommunication: ContentRepoCommunication,
                        appServerCommunication: AppServerCommunication,
                        appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings, config: Config)
                       (implicit materializer: Materializer) extends PdfGenerationActor {

  import ProcessGeneration._

  /** Timeout for each sub-task. */
  implicit val timeout: Timeout = Timeout(1, HOURS)

  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher

  /*
   * Actors for sub-tasks.
   */

  val stepFetchTemplateData: ActorRef = context.actorOf(
    Props(new StepFetchTemplateData(keyValueStoreCommunication)), "fetchData")
  val stepEvaluateTemplate: ActorRef = context.actorOf(Props(new StepEvaluateData(new FileSystemHelper(config))),
    "evalTemplate")
  val stepFetchAssets: ActorRef = context.actorOf(
    Props(new StepFetchAssets(new AppServerCommunication(keyValueStoreCommunication, appServerDispatcher, pluginSettings),
      new FileSystemHelper(config))), "fetchAssets")
  val stepPrepareFiles: ActorRef = context.actorOf(
    Props(new StepPrepareFiles(new FileSystemHelper(config))), "prepareFiles")
  val stepConvert: ActorRef = context.actorOf(
    Props(new StepConvert(config)), "convert")
  val stepFetchMergeResources: ActorRef = context.actorOf(
    Props(new StepFetchMergeResources(keyValueStoreCommunication, contentRepoCommunication)), "fetchMergeResources")
  val stepMerge: ActorRef = context.actorOf(Props[StepMerge], "merge")
  val stepPublish: ActorRef = context.actorOf(
    Props(new StepPublishFinishedPdf(keyValueStoreCommunication, contentRepoCommunication)), "publishFinishedPdf")

  var originalSender: ActorRef = context.system.deadLetters

  override def receive: PartialFunction[Any, Unit] = {
    case job: PdfGenerationJob =>
      originalSender = sender()
      val jobId = job.jobId
      log.info(s"[Job $jobId] Starting job ...")
      val process = for {
        templateData <- fetchTemplateData(job)
        evaluatedTemplate <- evaluateTemplate(templateData)
        templateWithAssets <- fetchAssets(evaluatedTemplate)
        preparationResult <- prepareFiles(templateWithAssets)
        convertResult <- convert(preparationResult)
        pdfWithMergeResources <- fetchMergeResources(convertResult)
        mergedPdf <- merge(pdfWithMergeResources)
        publishResult <- publishFinishedPdf(mergedPdf)
      } yield publishResult
      process andThen {
        handleResult(job)
      } andThen {
        case _ => context stop self
      }
    case job: DirectTemplateGenerationJob =>
      originalSender = sender()
      log.info(s"[Job ${job.jobId}] Starting direct template generation job ...")
      val process = for {
        evaluatedTemplate <- evaluateTemplate(TemplateData(job, job.replacements))
        templateWithAssets <- fetchAssets(evaluatedTemplate)
        preparationResult <- prepareFiles(templateWithAssets)
        convertResult <- convert(preparationResult)
        mergedPdf <- merge(PdfWithMergeResources(convertResult.job, convertResult.pdfData, Seq()))
      } yield mergedPdf
      process andThen {
        handleResult(job)
      } andThen {
        case _ => context stop self
      }
    case job: DirectGenerationJob =>
      originalSender = sender()
      log.info(s"[Job ${job.jobId}] Starting direct generation job ...")
      val process = for {
        convertResult <- convertString(job)
      } yield convertResult
      process andThen {
        handleResult(job)
      } andThen {
        case _ => context stop self
      }
    case job: MergeGenerationJob =>
      originalSender = sender()
      log.info(s"[Job ${job.jobId}] Starting merge generation job ...")
      val bytesOfEmptyPdf = new ByteArrayOutputStream()
      new PDDocument().save(bytesOfEmptyPdf)
      val process = for {
        pdfWithMergeResources <- fetchMergeResources(ConvertResult(job, bytesOfEmptyPdf.toByteArray))
        mergedPdf <- merge(pdfWithMergeResources)
        publishResult <- publishFinishedPdf(mergedPdf)
      } yield publishResult
      process andThen {
        handleResult(job)
      } andThen {
        case _ => context stop self
      }

  }

  private def handleResult(job: PdfJobAbstraction): PartialFunction[Try[_], _] = {
    case Success(result) =>
      log.info(s"[Job ${job.jobId}] finished successfully.")
      originalSender ! result
    case Failure(exception) => onFailure(job, exception)
      originalSender ! exception
  }

  /** Subtask: Fetch template data. */
  private def fetchTemplateData(job: PdfGenerationJob): Future[TemplateData] = {
    handleResult[TemplateData](stepFetchTemplateData ? job, ProcessErrorTemplateEvaluation,
      "FetchTemplateData", job.jobId)
  }

  /** Subtask: Evaluate template data. */
  private def evaluateTemplate(templateData: TemplateData): Future[EvaluatedTemplate] = {
    handleResult[EvaluatedTemplate](stepEvaluateTemplate ? templateData, ProcessErrorTemplateEvaluation,
      "EvaluateTemplate", templateData.job.jobId)
  }

  /** Subtask: Fetch assets. */
  private def fetchAssets(evaluatedTemplate: EvaluatedTemplate): Future[TemplateWithAssets] = {
    handleResult[TemplateWithAssets](stepFetchAssets ? evaluatedTemplate, ProcessErrorTemplatePreparation,
      "FetchAssets", evaluatedTemplate.job.jobId)
  }

  /** Subtask: Prepare files. */
  private def prepareFiles(template: TemplateWithAssets): Future[PreparationResult] = {
    handleResult[PreparationResult](stepPrepareFiles ? template, ProcessErrorTemplatePreparation,
      "PrepareFiles", template.job.jobId)
  }

  private def convertString(job: DirectGenerationJob): Future[ConvertResult] = {
    handleResult[ConvertResult](stepConvert ? job, ProcessErrorPdfConvert, "Convert", job.jobId)
  }

  /** Subtask: Convert HTML to PDF. */
  private def convert(preparationResult: PreparationResult): Future[ConvertResult] = {
    handleResult[ConvertResult](stepConvert ? preparationResult, ProcessErrorPdfConvert,
      "Convert", preparationResult.job.jobId)
  }

  /** Subtask: Fetch merge resources. */
  private def fetchMergeResources(convertResult: ConvertResult): Future[PdfWithMergeResources] = {
    handleResult[PdfWithMergeResources](stepFetchMergeResources ? convertResult, ProcessErrorPdfConvert,
      "FetchMergeResource", convertResult.job.jobId)
  }

  /** Subtask: Merge. */
  private def merge(pdfWithMergeResources: PdfWithMergeResources): Future[MergedPdf] = {
    handleResult[MergedPdf](stepMerge ? pdfWithMergeResources, ProcessErrorPdfConvert,
      "Merge", pdfWithMergeResources.job.jobId)
  }


  /** Subtask: Publish finished PDF. */
  private def publishFinishedPdf(mergedPdf: StepMerge.MergedPdf): Future[PublishingResult] = {
    handleResult[PublishingResult](stepPublish ? mergedPdf, ProcessErrorPublish,
      "PublishFinishedPdf", mergedPdf.job.jobId)
  }

  /**
    * Error handler for failed generations.
    *
    * @param job       generation job that was executed
    * @param exception exception that was caught
    */
  private def onFailure(job: PdfJobAbstraction, exception: Throwable): Unit = {
    val jobId = job.jobId
    val error = exception match {
      case ProcessException(step, msg) => s"Error during $step: $msg"
      case other => other.toString
    }
    log.error(s"[Job $jobId] Processing Error: $error")
    appServerCommunication.logError(jobId, s"throwMe:$error")(job.requestSource, job.userSession)
  }

  private def handleResult[T: ClassTag](future: Future[Any], customException: String => ProcessException,
                                        stepName: String, jobId: String): Future[T] = {
    future map {
      case expected: T => expected
      case error: SubTaskException =>
        log.error(s"[Job $jobId] Unexpected error", error.exc)
        originalSender ! customException(s"Unexpected error in step $stepName: ${error.exc.getMessage}")
        throw customException(s"Unexpected error in step $stepName: ${error.exc.getMessage}")
      case other =>
        log.warn(s"[Job $jobId] Unexpected result: $other")
        originalSender ! customException(s"Unexpected result in step $stepName")
        throw customException(s"Unexpected result in step $stepName")
    }
  }

}

object ProcessGeneration {

  /**
    * PDF Generation Job.
    *
    * @param jobId           unique ID of job
    * @param session         session ID
    * @param templateName    name of target template
    * @param templateData    binary data of target template
    * @param stylesheetData  the css style sheet data
    * @param pdfConfig       config for PDF generation
    * @param userSession     the user session
    * @param requestSource   the original request source
    * @param contentFileInfo content file info if the created pdf should be saved to the content repo
    */
  case class PdfGenerationJob(jobId: String,
                              session: String,
                              templateName: String,
                              templateData: Array[Byte],
                              stylesheetData: Array[Byte],
                              pdfConfig: PdfConfig,
                              userSession: UserSession,
                              requestSource: RequestSource,
                              hasCustomHeader: Boolean,
                              hasCustomFooter: Boolean,
                              headerData: Option[Array[Byte]],
                              footerData: Option[Array[Byte]],
                              contentFileInfo: Option[ContentFileInfo] = None) extends PdfGenerationJobAbstraction

  case class MergeGenerationJob(jobId: String,
                                keyValueStoreId: Option[String],
                                contentRepoIds: Option[Seq[Int]],
                                pdfConfig: PdfConfig,
                                userSession: UserSession,
                                requestSource: RequestSource,
                                hasCustomHeader: Boolean,
                                hasCustomFooter: Boolean,
                                contentFileInfo: Option[ContentFileInfo]
                        ) extends PdfJobAbstraction

  case class DirectTemplateGenerationJob(jobId: String,
                                         replacements: Map[String, Any],
                                         templateName: String,
                                         templateData: Array[Byte],
                                         stylesheetData: Array[Byte],
                                         pdfConfig: PdfConfig,
                                         requestSource: RequestSource,
                                         userSession: UserSession,
                                         hasCustomHeader: Boolean,
                                         hasCustomFooter: Boolean,
                                         headerData: Option[Array[Byte]],
                                         footerData: Option[Array[Byte]]) extends PdfGenerationJobAbstraction

  case class DirectGenerationJob(jobId: String,
                                 htmlString: String,
                                 pdfConfig: PdfConfig,
                                 requestSource: RequestSource,
                                 userSession: UserSession,
                                 hasCustomHeader: Boolean,
                                 hasCustomFooter: Boolean) extends PdfJobAbstraction

  /**
    * Excpetion occured druing task.
    *
    * @param exc exception that occured
    */
  case class SubTaskException(exc: Exception)

  /**
    * Exception in the process.
    *
    * @param step    step name
    * @param message error message
    */
  case class ProcessException(step: String, message: String) extends Exception(message)

  def ProcessErrorTemplateEvaluation(message: String): ProcessException = ProcessException("Template Evaluation", message)

  def ProcessErrorTemplatePreparation(message: String): ProcessException = ProcessException("Template Preparation", message)

  def ProcessErrorPdfConvert(message: String): ProcessException = ProcessException("PDF Conversion", message)

  def ProcessErrorPdfMerge(message: String): ProcessException = ProcessException("PDF Merging", message)

  def ProcessErrorPublish(message: String): ProcessException = ProcessException("Publishing", message)
}