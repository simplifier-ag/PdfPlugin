package com.itizzimo.pdfplugin.slots

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.itizzimo.pdfplugin.Constants._
import com.itizzimo.pdfplugin.RestMessages._
import com.itizzimo.pdfplugin.TemplateStore.SuccessfulWithValue
import com.itizzimo.pdfplugin.controller.GenerationController
import com.itizzimo.pdfplugin.controller.GenerationController._
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import com.typesafe.config.Config
import io.swagger.annotations._

import javax.ws.rs.Path
import scala.concurrent.Future

class GenerationSlotService(generationController: GenerationController) extends GenericPdfSlotService {
  override val slotNames: Seq[String] = Seq(
    "generatePdf",
    "generatePdfHttp",
    "generatePdfFromTemplate",
    "generatePdfFromTemplateHttp",
    "generatePdfToContentRepo",
    "generatePdfToContentRepoHttp",
    "generatePdfFromHtml",
    "generatePdfFromHtmlHttp",
    "mergePdfToContentRepo"
  )

  override def serviceRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route = {
    path("generatePdfHttp" | "generatePdf") {
      requestHandler(generationController.startPdfGeneration, generatePdfFailure, ACTION_GENERATE, PDF)
    } ~
      path("generatePdfFromTemplateHttp" | "generatePdfFromTemplate") {
        asyncRequestHandler(generationController.startPdfFromTemplateGeneration, generatePdfFailure, ACTION_GENERATE, PDF)
      } ~
      path("generatePdfToContentRepoHttp" | "generatePdfToContentRepo") {
        asyncRequestHandler(generationController.startPdfToContentRepoGeneration, generatePdfFailure, ACTION_GENERATE, PDF)
      } ~
      path("generatePdfFromHtmlHttp" | "generatePdfFromHtml") {
        asyncRequestHandler(generationController.startPdfFromHtmlGeneration, generatePdfFailure, ACTION_GENERATE, PDF)
      } ~
      path("mergePdfToContentRepoHttp" | "mergePdfToContentRepo") {
        asyncRequestHandler(generationController.mergePdfToContentRepo, generatePdfFailure, ACTION_GENERATE, PDF)
      }
  }
}

object GenerationSlotService {
  def apply(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings, config: Config)
           (implicit materializer: Materializer): GenerationSlotService =
    new GenerationSlotService(GenerationController(appServerDispatcher, pluginSettings, config))

  @Api(tags = Array("Generation"), authorizations = Array(new Authorization("basicAuth")), description = "all slots require plugin permission 'generatePdf'")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Unauthorized")
  ))
  @Path("/client/2.0/pluginSlot/pdfPlugin/")
  trait Documentation {

    @ApiOperation(httpMethod = "POST", value = "Generate a pdf")
    @Path("/generatePdf")
    protected def startPdfGeneration(@ApiParam(required=true) request: GeneratePdfRequest): GeneratePdfResponse

    @ApiOperation(httpMethod = "POST", value = "Generate pdf from html template",
      notes="The template can be created through Plugin settings.")
    @Path("/generatePdfFromTemplate")
    protected def startPdfFromTemplateGeneration(@ApiParam(required=true) request: GeneratePdfFromTemplateRequest): SuccessfulWithValue

    @ApiOperation(httpMethod = "POST", value = "Generate pdf to content repo")
    @Path("/generatePdfToContentRepo")
    def startPdfToContentRepoGeneration(@ApiParam(required=true, access="Permission 'generatePdF'") request: GeneratePdfToContentRepoRequest): SuccessfulWithValue

    @ApiOperation(httpMethod = "POST", value = "Generate pdf from html")
    @Path("/generatePdfFromHtml")
    def startPdfFromHtmlGeneration(@ApiParam(required=true) request: GeneratePdfFromHtmlRequest): Future[SuccessfulWithValue]

  }

}
