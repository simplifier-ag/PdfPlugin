package com.itizzimo.pdfplugin

import com.itizzimo.pdfplugin.interfaces._
import com.itizzimo.pdfplugin.permission.PdfPluginPermission
import io.simplifier.pluginbase._
import io.simplifier.pluginapi.RegistrationData.PluginConfigurationView
import io.simplifier.pluginbase.interfaces.PluginBaseHttpService
import io.simplifier.pluginbase.permission.PluginPermissionObject

import scala.concurrent.Future

object Defaults {
  final val PLUGIN_DESCRIPTION_DEFAULT: PluginDescription =
    PluginDescription("Pdf Plugin", "Used to create pdfs from html", "2.0", "Simplifier AG", None)
}

object PdfPlugin extends PdfPluginLogic with SimplifierPlugin {
  val pluginSecret: String = byDeployment.PluginRegistrationSecret()
}

abstract class PdfPluginLogic extends SimplifierPluginLogic(Defaults.PLUGIN_DESCRIPTION_DEFAULT, "pdfPlugin") {

  import ACTOR_SYSTEM.dispatcher

  val pluginPermission = PdfPluginPermission

  override def pluginPermissions: Seq[PluginPermissionObject] = Seq(pluginPermission)

  override def startPluginServices(basicState: SimplifierPlugin.BasicState): Future[PluginBaseHttpService] = Future {
    val slotInterface = Some(SlotInterface(basicState.dispatcher, basicState.settings, basicState.config,
      basicState.pluginDescription, pluginPermission))
    val proxyInterface = None
    val configInterface = Some(new ConfigInterface("", "assets/",
      Seq(PluginConfigurationView("PdfPlugin", "io.simplifier.ui5.plugin.pdfPlugin.adminui.pdfGenerator"))))
    val documentationInterface = Some(new PluginDocumentationService())
    new PluginBaseHttpService(basicState.pluginDescription, basicState.settings, basicState.appServerInformation,
      proxyInterface, slotInterface, configInterface, documentationInterface)
  }
}
