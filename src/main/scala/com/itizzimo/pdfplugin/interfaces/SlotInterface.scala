package com.itizzimo.pdfplugin.interfaces

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.itizzimo.pdfplugin.permission.PdfPluginPermission.{characteristicAdministrate, characteristicView}
import com.itizzimo.pdfplugin.permission.PermissionHandler
import com.itizzimo.pdfplugin.slots.{AdministrationSlotService, CreationSlotService, GenerationSlotService}
import io.simplifier.pluginbase.{PluginDescription, PluginSettings}
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.interfaces.{AppServerDispatcher, SlotInterfaceService}
import io.simplifier.pluginbase.permission.PluginPermissionObject
import com.typesafe.config.Config

class SlotInterface(dispatcher: AppServerDispatcher,
                    pluginDescription: PluginDescription,
                    pluginPermission: PluginPermissionObject,
                    administrationSlotService: AdministrationSlotService,
                    generationSlotService: GenerationSlotService,
                    creationSlotService: CreationSlotService,
                    permissionHandler: PermissionHandler)
  extends SlotInterfaceService(dispatcher, pluginDescription, pluginPermission) {

  /** Base-URL relative to http service root */
  override val baseUrl: String = "slots"

  override def pluginSlotNames: Seq[String] =
    administrationSlotService.slotNames ++
    generationSlotService.slotNames ++
    creationSlotService.slotNames

  override protected def checkAdministratePermission()(implicit userSession: UserSession, requestSource: RequestSource): Unit = {
    permissionHandler.checkAdditionalPermission(characteristicAdministrate)
  }

  /**
    * Plugin-specific inner route handling slot requests
    *
    * @param requestSource plugin request source
    * @param userSession   authenticated user session
    * @return service route
    */
  override def serviceRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route =
    administrationSlotService.serviceRoute ~
    generationSlotService.serviceRoute ~
    creationSlotService.serviceRoute
}

object SlotInterface {

  def apply(dispatcher: AppServerDispatcher, settings: PluginSettings,
            config: Config, pluginDescription: PluginDescription, pluginPermission: PluginPermissionObject)
           (implicit materializer: Materializer): SlotInterface =
    new SlotInterface(
      dispatcher,
      pluginDescription,
      pluginPermission,
      AdministrationSlotService(dispatcher, settings, config),
      GenerationSlotService(dispatcher, settings, config),
      CreationSlotService(dispatcher, settings),
      new PermissionHandler(dispatcher, settings))

}
