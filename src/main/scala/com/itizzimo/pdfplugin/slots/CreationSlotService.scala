package com.itizzimo.pdfplugin.slots
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.itizzimo.pdfplugin.Constants._
import com.itizzimo.pdfplugin.RestMessages._
import com.itizzimo.pdfplugin.TemplateStore.TemplateOperationResult
import com.itizzimo.pdfplugin.controller.CreationController
import com.itizzimo.pdfplugin.controller.CreationController.CreatePdfRequest
import com.itizzimo.pdfplugin.permission.PermissionHandler
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import io.simplifier.pluginbase.slotservice.Constants._
import io.swagger.annotations.{Api, ApiOperation, Authorization}

import javax.ws.rs.Path

class CreationSlotService(creationController: CreationController) extends GenericPdfSlotService {

  override val slotNames: Seq[String] = Seq("createPdfWithContent", "createPdfWithContentHttp")

  override def serviceRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route = {
    path("createPdfWithContentHttp" | "createPdfWithContent") {
      requestHandler(creationController.startPdfCreationWithContent, createPdfFailure, ACTION_CREATE, PDF)
    }
  }
}

object CreationSlotService {

  def apply(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings)
           (implicit materializer: Materializer): CreationSlotService =
    new CreationSlotService(CreationController(appServerDispatcher, pluginSettings,
      new PermissionHandler(appServerDispatcher, pluginSettings)))

  @Api(tags = Array("Creation"), authorizations = Array(new Authorization("basicAuth")))
  @Path("/client/2.0/pluginSlot/pdfPlugin/")
  trait Documentation {
    @ApiOperation(httpMethod = "POST", value = "Generate pdf from file of content repo")
    @Path("/createPdfWithContent")
    def startPdfCreationWithContent(request: CreatePdfRequest): TemplateOperationResult
  }

}