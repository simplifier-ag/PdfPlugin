package com.itizzimo.pdfplugin

import com.itizzimo.pdfplugin.slots.{AdministrationSlotService, CreationSlotService, GenerationSlotService}
import io.simplifier.pluginbase.interfaces.{DocumentationInterfaceService, SlotInterfaceService}

class PluginDocumentationService extends DocumentationInterfaceService {

  override val apiClasses: Set[Class[_]] = Set(
    classOf[GenerationSlotService.Documentation],
    classOf[CreationSlotService.Documentation],
    classOf[AdministrationSlotService.Documentation],
    classOf[SlotInterfaceService.Documentation]
  )

  override val title: String = "Pdf Plugin Client API"
  override val description: String = "Plugin to generate pdf files"
  override val externalDocsDescription: String = "Documentation for PDF Plugin"
}