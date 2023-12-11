package com.itizzimo.pdfplugin

object Constants {

  final val TEMPLATE: String = "Template"
  final val TEMPLATES: String = "Templates"

  final val PDF: String = "Pdf"

  final val ACTION_ADD: String = "add"
  final val ACTION_ADDED: String = "added"
  final val ACTION_EDIT: String = "edit"
  final val ACTION_EDITED: String = "edited"
  final val ACTION_FETCH: String = "fetch"
  final val ACTION_FETCHED: String = "fetched"

  final val ACTION_GENERATE: String = "generate"
  final val ACTION_GENERATED: String = "generated"

  // Error messages

  final val INVALID_TEMPLATE_NAME: String = "A-000"
  final val TEMPLATE_NOT_EXISTING: String = "A-001"
  final val TEMPLATE_NAME_ALREADY_IN_USE: String = "A-002"

  final val MERGE_NOT_POSSIBLE_DUE_TO_ENCRYPTION: String = "A-003"
}
