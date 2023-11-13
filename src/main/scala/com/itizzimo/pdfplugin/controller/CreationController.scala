package com.itizzimo.pdfplugin.controller

import java.util.UUID
import akka.actor.{ActorRef, ActorSystem, Props, Terminated}
import akka.stream.Materializer
import io.simplifier.pluginapi.helper.PluginLogger
import io.simplifier.pluginbase.util.api.ApiMessage
import com.itizzimo.pdfplugin.DocumentConfig.getPdfConfigFromJSON
import com.itizzimo.pdfplugin.TemplateStore.{SuccessfulWithValue, TemplateOperationResult}
import com.itizzimo.pdfplugin.actor.ProcessCreation
import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfCreationJob
import com.itizzimo.pdfplugin.controller.GenerationController.StartGenerationResult
import com.itizzimo.pdfplugin.helpers.{AppServerCommunication, ContentRepoCommunication, KeyValueStoreCommunication}
import com.itizzimo.pdfplugin.permission.PdfPluginPermission.characteristicEdit
import com.itizzimo.pdfplugin.permission.PermissionHandler
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import com.typesafe.config.ConfigFactory
import io.simplifier.pluginbase.util.json.JSONCompatibility._
import scala.concurrent.Future
import scala.util.Try

class CreationController(keyValueStoreCommunication: KeyValueStoreCommunication,
                         contentRepoCommunication: ContentRepoCommunication,
                         appServerCommunication: AppServerCommunication,
                         permissionHandler: PermissionHandler) extends PluginLogger {

  import CreationController._

  val actorSystem: ActorSystem = initSystem

  /** Initialize actor system. */
  def initSystem: ActorSystem = {
    log.debug("Init PDF Creation actor system ...")
    ActorSystem("pdfCreation", ConfigFactory.defaultOverrides)
  }

  /** Actor to handle the PDF Generation process. */
  val processActor: ActorRef = actorSystem.actorOf(Props(new ProcessCreation(keyValueStoreCommunication,
    contentRepoCommunication, appServerCommunication)), "processPdfCreation")

  /** Initialize PDF Generator. */
  def init(): Unit = {
    log.info("Init PDF Creator")
  }

  /** Shutdown actor system. */
  def shutdown: Future[Terminated] = {
    log.info("Shutdown PDF Creator actor system ...")
    actorSystem.terminate()
  }

  /**
    * Start generation of new PDF with given content and without template.
    *
    * @return template operation result
    */
  def startPdfCreationWithContent(request: CreatePdfRequest)
                                 (implicit requestSource: RequestSource, userSession: UserSession): Try[TemplateOperationResult] = Try {
    permissionHandler.checkAdditionalPermission(characteristicEdit)
    val jobId = UUID.randomUUID.toString
    val pdfConfigJson = request.config flatMap {
      configString =>
        val parsed = parseJsonOrEmptyString(configString).toOption
        if (parsed.isEmpty) {
          log.warn(s"Unable to parse PDF config from String: $configString")
        }
        parsed
    }
    val pdfConfig = getPdfConfigFromJSON(pdfConfigJson)
    log.debug(s"Starting PDF Creation job (session: ${request.sessions}, user: $userSession)")
    processActor ! PdfCreationJob(jobId, request.sessions, pdfConfig, userSession, requestSource,
      hasCustomHeader = false, hasCustomFooter = false)
    SuccessfulWithValue(StartGenerationResult(jobId))

  }

}

object CreationController {

  def apply(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings, permissionHandler: PermissionHandler)
           (implicit materializer: Materializer): CreationController = {
    val keyValueStoreCommunication = new KeyValueStoreCommunication(appServerDispatcher, pluginSettings)
    new CreationController(keyValueStoreCommunication, new ContentRepoCommunication(appServerDispatcher, pluginSettings),
      new AppServerCommunication(keyValueStoreCommunication, appServerDispatcher, pluginSettings), permissionHandler)
  }

  case class CreatePdfRequest(sessions: Seq[String], config: Option[String]) extends ApiMessage

}