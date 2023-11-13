package com.itizzimo.pdfplugin

import io.simplifier.pluginbase.slotservice.GenericRestMessages
import com.itizzimo.pdfplugin.Constants._
import io.simplifier.pluginbase.slotservice.Constants._

object RestMessages extends GenericRestMessages {

  val (addTemplateSuccess, addTemplateFailure) = mkRestMessagePair(TEMPLATE, ACTION_ADDED)
  val (deleteTemplateSuccess, deleteTemplateFailure) = mkRestMessagePair(TEMPLATE, ACTION_DELETED)
  val (editTemplateSuccess, editTemplateFailure) = mkRestMessagePair(TEMPLATE, ACTION_EDITED)
  val (fetchTemplateSuccess, fetchTemplateFailure) = mkRestMessagePair(TEMPLATE, ACTION_FETCHED)
  val (listTemplatesSuccess, listTemplatesFailure) = mkRestMessagePair(TEMPLATES, ACTION_LISTED, plural = true)

  val (generatePdfSuccess, generatePdfFailure) = mkRestMessagePair(PDF, ACTION_GENERATED)

  val (createPdfSuccess, createPdfFailure) = mkRestMessagePair(PDF, ACTION_CREATED)
}