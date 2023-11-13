package com.itizzimo.pdfplugin.slots

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.itizzimo.pdfplugin.Constants._
import com.itizzimo.pdfplugin.RestMessages._
import com.itizzimo.pdfplugin.controller.AdministrationController
import com.itizzimo.pdfplugin.controller.AdministrationController._
import com.itizzimo.pdfplugin.helpers.DirectoryTemplateStore
import com.itizzimo.pdfplugin.permission.PermissionHandler
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import io.simplifier.pluginbase.slotservice.Constants._
import io.simplifier.pluginbase.slotservice.GenericSlotService
import com.typesafe.config.Config
import io.swagger.annotations.{Api, ApiOperation, Authorization}

import javax.ws.rs.Path

class AdministrationSlotService(administrationController: AdministrationController) extends GenericSlotService {
  override val slotNames: Seq[String] = Seq("adminTemplateAdd", "adminTemplateDelete", "adminTemplateEdit",
    "adminTemplateFetch", "adminTemplateList")

  override def serviceRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route =
    path("adminTemplateAdd") {
      requestHandler(administrationController.addTemplate, addTemplateFailure, ACTION_ADD, TEMPLATE)
    } ~
      path("adminTemplateDelete") {
        requestHandler(administrationController.deleteTemplate, deleteTemplateFailure, ACTION_DELETE, TEMPLATE)
      } ~
      path("adminTemplateEdit") {
        requestHandler(administrationController.editTemplate, editTemplateFailure, ACTION_EDIT, TEMPLATE)
      } ~
      path("adminTemplateFetch") {
        requestHandler(administrationController.fetchTemplate, fetchTemplateFailure, ACTION_FETCH, TEMPLATE)
      } ~
      path("adminTemplateList") {
        complete(resultHandler(administrationController.listTemplates, listTemplatesFailure, ACTION_LIST, TEMPLATES))
      }
}

object AdministrationSlotService {
  def apply(dispatcher: AppServerDispatcher, settings: PluginSettings, config: Config): AdministrationSlotService =
    new AdministrationSlotService(new AdministrationController(new DirectoryTemplateStore(config),
      new PermissionHandler(dispatcher, settings)))

  @Api(tags = Array("Administration"), authorizations = Array(new Authorization("basicAuth")))
  @Path("/client/2.0/pluginSlot/pdfPlugin/")
  trait Documentation {
    @ApiOperation(httpMethod = "POST", value="Add a new template. This template can be used to generate pdf file layouts.")
    @Path("/adminTemplateAdd")
    def addTemplate(request: HttpParamTemplateAdd): Response

    @ApiOperation(httpMethod = "POST", value="Delete a template.")
    @Path("/adminTemplateDelete")
    def deleteTemplate(request: DeleteTemplateRequest): Response

    @ApiOperation(httpMethod = "POST", value="Edit an already existing template.")
    @Path("/adminTemplateEdit")
    def editTemplate(arg: EditTemplateRequest): Response

    @ApiOperation(httpMethod = "POST", value="Get an already existing template.")
    @Path("/adminTemplateFetch")
    def fetchTemplate(arg: FetchTemplateRequest): FetchTemplateResponse

    @ApiOperation(httpMethod = "POST", value="List all existing templates.")
    @Path("/adminTemplateList")
    def listTemplates: ListTemplatesResponse
  }

}
