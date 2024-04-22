package com.itizzimo.pdfplugin.controller

import akka.actor.{ActorRef, ActorSystem, Props, Terminated}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import io.simplifier.pluginapi.helper.{Base64Encoding, PluginLogger}
import io.simplifier.pluginbase.util.api.ApiMessage
import io.simplifier.pluginbase.util.json.JSONCompatibility.parseJsonOrEmptyString
import com.itizzimo.pdfplugin.DocumentConfig.getPdfConfigFromJSON
import com.itizzimo.pdfplugin.Constants.MERGE_NOT_POSSIBLE_DUE_TO_ENCRYPTION
import io.simplifier.pluginbase.slotservice.GenericFailureHandling.OperationFailureMessage
import com.itizzimo.pdfplugin.RestMessages._
import com.itizzimo.pdfplugin.TemplateStore.{SuccessfulWithValue, TemplateNotExisting}
import com.itizzimo.pdfplugin.actor.ProcessGeneration
import com.itizzimo.pdfplugin.actor.ProcessGeneration._
import com.itizzimo.pdfplugin.actor.StepConvert.ConvertResult
import com.itizzimo.pdfplugin.actor.StepMerge.MergedPdf
import com.itizzimo.pdfplugin.actor.StepPublishFinishedPdf.PublishingResult
import com.itizzimo.pdfplugin.helpers.{AppServerCommunication, ContentRepoCommunication, DirectoryTemplateStore, KeyValueStoreCommunication}
import com.itizzimo.pdfplugin.permission.PdfPluginPermission.characteristicGeneratePdf
import com.itizzimo.pdfplugin.permission.PermissionHandler
import com.itizzimo.pdfplugin.{ExtendedPdfConfig, TemplateContentData}
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import io.simplifier.pluginbase.slotservice.GenericRestMessages.RestMessage
import com.typesafe.config.{Config, ConfigFactory}
import io.swagger.annotations.ApiModelProperty
import org.json4s._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class GenerationController(keyValueStoreCommunication: KeyValueStoreCommunication, permissionHandler: PermissionHandler,
                           appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings, config: Config,
                           templateStore: DirectoryTemplateStore)(implicit materializer: Materializer)
  extends Base64Encoding with PluginLogger {

  import GenerationController._

  implicit val timeout: Timeout = pluginSettings.timeout

  val actorSystem: ActorSystem = initSystem

  /** Initialize actor system. */
  def initSystem: ActorSystem = {
    log.debug("Init PDF Generator actor system ...")
    ActorSystem("pdfGeneration", ConfigFactory.defaultOverrides)
  }

  private def createActor(): ActorRef = {
    /** Actor to handle the PDF Generation process. */
    actorSystem.actorOf(Props(new ProcessGeneration(keyValueStoreCommunication,
      new ContentRepoCommunication(appServerDispatcher, pluginSettings),
      new AppServerCommunication(keyValueStoreCommunication, appServerDispatcher, pluginSettings),
      appServerDispatcher, pluginSettings, config)))
  }

  /** Initialize PDF Generator. */
  def init(): Unit = {
    log.info("Init PDF Generator")
  }

  /** Shutdown actor system. */
  def shutdown: Future[Terminated] = {
    log.info("Shutdown PDF Generator actor system ...")
    actorSystem.terminate()
  }

  /**
    * Start generation of new PDF.
    *
    * @return template operation result
    */
  def startPdfGeneration(request: GeneratePdfRequest)
                        (implicit requestSource: RequestSource,
                         userSession: UserSession): Try[GeneratePdfResponse] = Try {
    permissionHandler.checkPermission(characteristicGeneratePdf)
    getTemplate(request.template) match {
      case None => throw TemplateNotExisting(request.template)
      case Some(templateContent) =>
        val jobId = UUID.randomUUID.toString
        val pdfConfig = getConfig(request.config, templateContent.headerPath, templateContent.footerPath)
        log.debug(s"Starting PDF Generation job with template '$request.template' (session: $request.session, user: $userSession)")
        createActor() ! PdfGenerationJob(jobId, request.session, request.template, templateContent.content,
          templateContent.stylesheet, pdfConfig.pdfConfig, userSession, requestSource, pdfConfig.hasCustomHeader,
          pdfConfig.hasCustomFooter, templateContent.header, templateContent.footer)
        GeneratePdfResponse(StartGenerationResult(jobId))
    }

  }

  def startPdfToContentRepoGeneration(request: GeneratePdfToContentRepoRequest)
                                     (implicit requestSource: RequestSource,
                                      userSession: UserSession): Future[SuccessfulWithValue] = {
    permissionHandler.checkPermission(characteristicGeneratePdf)
    getTemplate(request.templateName) match {
      case None => Future.failed(TemplateNotExisting(request.templateName))
      case Some(templateContent) =>
        val jobId = UUID.randomUUID.toString
        val pdfConfig = getConfig(request.config, templateContent.headerPath, templateContent.footerPath)
        log.debug(s"Starting PDF Generation to content repo job with template '${request.templateName}' (session: ${request.session}, user: $userSession)")

        for {
          x <- createActor() ? PdfGenerationJob(jobId, request.session, request.templateName,
            templateContent.content, templateContent.stylesheet, pdfConfig.pdfConfig, userSession, requestSource,
            pdfConfig.hasCustomHeader, pdfConfig.hasCustomFooter, templateContent.header, templateContent.footer, Some(request.contentFileInfo))
        } yield {
          x match {
            case PublishingResult(job: PdfGenerationJob) if job.contentFileInfo.isDefined =>
              val fileId = job.contentFileInfo.get.fileId.getOrElse(throw FileIdNotFoundException)
              SuccessfulWithValue(fileId)
            case pr: PublishingResult =>
              log.error(s"received publish result without content file info $pr")
              throw NoContentFileInfoException
            case ex: Throwable => throw UnexpectedException(ex)
          }
        }
      }
    }

  def startPdfFromTemplateGeneration(request: GeneratePdfFromTemplateRequest)
                                    (implicit requestSource: RequestSource,
                                     userSession: UserSession): Future[SuccessfulWithValue] = {
    permissionHandler.checkPermission(characteristicGeneratePdf)
    getTemplate(request.templateName) match {
      case None => Future.failed(TemplateNotExisting(request.templateName))
      case Some(templateContent) =>
        val jobId = UUID.randomUUID.toString
        val pdfConfig = getConfig(request.config, templateContent.headerPath, templateContent.footerPath)
        for {
          x <- createActor() ? DirectTemplateGenerationJob(jobId, request.replacements.values, request.templateName,
            templateContent.content, templateContent.stylesheet, pdfConfig.pdfConfig, requestSource, userSession,
            pdfConfig.hasCustomHeader, pdfConfig.hasCustomFooter, templateContent.header, templateContent.footer)
        } yield {
          x match {
            case MergedPdf(_, data) =>
              val b64Data = encodeB64(data)
              SuccessfulWithValue(b64Data)
            case ex: Throwable => throw UnexpectedException(ex)
          }
        }
    }
  }

  def mergePdfToContentRepo(request: MergePdfToContentRepoRequest)
                           (implicit requestSource: RequestSource,
                            userSession: UserSession): Future[SuccessfulWithValue] = {
    permissionHandler.checkPermission(characteristicGeneratePdf)
    val jobId = UUID.randomUUID.toString
    val pdfConfig = getConfig(request.config, None, None)
    for {
      x <- createActor() ? MergeGenerationJob(jobId, request.keyValueStoreId, request.contentRepoIds, pdfConfig.pdfConfig,
        userSession, requestSource, hasCustomHeader = false, hasCustomFooter = false, request.contentFileInfo)
    } yield {
      x match {
        case PublishingResult(job: MergeGenerationJob) if job.contentFileInfo.isDefined =>
          val fileId = job.contentFileInfo.getOrElse(throw FileIdNotFoundException)
            .fileId.getOrElse(throw FileIdNotFoundException)
          SuccessfulWithValue(fileId)
        case ex: ProcessException if ex.getMessage contains "PDF is encrypted" =>
          throw OperationFailureMessage(ex.getMessage, MERGE_NOT_POSSIBLE_DUE_TO_ENCRYPTION)
        case ex: Throwable =>
          throw UnexpectedException(ex)
      }
    }
  }

  def startPdfFromHtmlGeneration(request: GeneratePdfFromHtmlRequest)
                                (implicit requestSource: RequestSource,
                                 userSession: UserSession): Future[SuccessfulWithValue] = {
    permissionHandler.checkPermission(characteristicGeneratePdf)
    val jobId = UUID.randomUUID.toString
    val config = getConfig(request.config, None, None)
    for {
      x <- createActor() ? DirectGenerationJob(jobId, request.htmlString, config.pdfConfig, requestSource, userSession,
        config.hasCustomHeader, config.hasCustomFooter)
    } yield {
      x match {
        case ConvertResult(_, data) =>
          val b64Data = encodeB64(data)
          SuccessfulWithValue(b64Data)
        case ex: Throwable => throw UnexpectedException(ex)
      }
    }
  }

  private def getTemplate(templateName: String): Option[TemplateContentData] = {
    templateStore.get(templateName)
  }

  private def getConfig(config: Option[String], headerPathOpt: Option[String],
                        footerPathOpt: Option[String]): ExtendedConfig = {
    val pdfConfigJson = config.flatMap { configString =>
      val parsed = parseJsonOrEmptyString(configString).toOption
      if (parsed.isEmpty) {
        log.warn(s"Unable to parse PDF config from String: $configString")
      }
      parsed
    }
    val pdfConfig = getPdfConfigFromJSON(pdfConfigJson)
    val hasCustomHeader = headerPathOpt.isDefined && !pdfConfig.hasHeaderParams
    val hasCustomFooter = footerPathOpt.isDefined && !pdfConfig.hasFooterParams
    if(hasCustomHeader) {
      pdfConfig.headerHtml := headerPathOpt.get
    }
    if(hasCustomFooter) {
      pdfConfig.footerHtml := footerPathOpt.get
    }
    ExtendedConfig(pdfConfig, hasCustomHeader, hasCustomFooter)
  }

  private val NoContentFileInfoException = ProcessException("Returning Content Info", "No content file info found")
  private val FileIdNotFoundException = ProcessException("Returning Content Info", "No file id found")
  private def UnexpectedException(ex: Throwable): ProcessException =
    ProcessException("Returning Content Info", s"Unexpected exception ${ex.getMessage}")
}

object GenerationController {

  def apply(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings, config: Config)
           (implicit materializer: Materializer): GenerationController =
    new GenerationController(KeyValueStoreCommunication(appServerDispatcher, pluginSettings),
      PermissionHandler(appServerDispatcher, pluginSettings), appServerDispatcher, pluginSettings, config,
      new DirectoryTemplateStore(config))

  case class StartGenerationResult(@ApiModelProperty(value="the content repo file id") jobId: String)

  case class GeneratePdfRequest(
                                 @ApiModelProperty(value="the session id of a file stored by content repo plugin") session: String,
                                 @ApiModelProperty(value="the name of a template created through plugin settings") template: String,
                                 @ApiModelProperty(value="the config as text for library 'wkhtmltopdf'") config: Option[String]) extends ApiMessage

  case class GeneratePdfResponse(
                                  @ApiModelProperty(value="the job id") value: StartGenerationResult,
                                  @ApiModelProperty(value="the return message") message: RestMessage = generatePdfSuccess,
                                  @ApiModelProperty(value="the flag if request was successful") success: Boolean = true) extends ApiMessage

  case class GeneratePdfFromHtmlRequest(htmlString: String, config: Option[String]) extends ApiMessage
  case class GeneratePdfFromTemplateRequest(templateName: String, replacements: JObject, config: Option[String]) extends ApiMessage
  case class GeneratePdfToContentRepoRequest(templateName: String, session: String, config: Option[String], contentFileInfo: ContentFileInfo) extends ApiMessage
  case class MergePdfToContentRepoRequest(keyValueStoreId: Option[String], contentRepoIds: Option[Seq[Int]],
                                          config: Option[String], contentFileInfo: Option[ContentFileInfo]) extends ApiMessage

  case class ContentFileInfo(@ApiModelProperty(dataType="java.lang.Integer") fileId: Option[Int], folderId: Int, fileName: String, fileDescription: Option[String], securitySchemeId: String,
                             permissionObjectType: String, permissionObjectId: String)

  case class ExtendedConfig(pdfConfig: ExtendedPdfConfig, hasCustomHeader: Boolean, hasCustomFooter: Boolean)
}