package com.itizzimo.pdfplugin.permission

import io.simplifier.pluginbase.permission.PluginPermissionObject
import io.simplifier.pluginbase.permission.PluginPermissionObjectCharacteristics.CheckboxCharacteristic

object PdfPluginPermission extends PluginPermissionObject {

  val characteristicGeneratePdf = "generatePdf"

  val characteristicManageTemplates = "manageTemplates"

  val characteristicView = "view"

  val characteristicEdit = "edit"

  val characteristicAdministrate = "administrate"

  /**
    * Name of the permission object.
    */
  override val name: String = "Plugin PDF Generation"
  /**
    * Technical Name of the permission object.
    */
  override val technicalName: String = PluginPermissionObject.getTechnicalName("PdfGeneration")
  /**
    * Description of the permission object.
    */
  override val description: String = "Plugin: PDF Generation"
  /**
    * Possible characteristics for the admin ui.
    */
  override val characteristics: Seq[CheckboxCharacteristic] = Seq(
    CheckboxCharacteristic(characteristicGeneratePdf, "Generate PDF", "Run PDF Generation"),
    CheckboxCharacteristic(characteristicManageTemplates, "Manage Templates", "Create, Edit, Delete PDF Templates"),
    CheckboxCharacteristic(characteristicView, "View", "View plugin content"),
    CheckboxCharacteristic(characteristicEdit, "Edit", "Edit plugin content"),
    CheckboxCharacteristic(characteristicAdministrate, "Administrate", "Administrate the plugin")
  )
}
