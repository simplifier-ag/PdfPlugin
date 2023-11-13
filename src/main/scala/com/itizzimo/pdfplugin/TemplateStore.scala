package com.itizzimo.pdfplugin

import io.simplifier.pluginbase.util.api.ApiMessage
import io.simplifier.pluginbase.slotservice.GenericFailureHandling.OperationFailureMessage
import com.itizzimo.pdfplugin.Constants._

import scala.util.Try

/**
 * Data class containing the HTML content of a template, the associated Stylesheet file and a preview JSON for the adminUI.
 */
case class TemplateContentData(content: Array[Byte], header: Option[Array[Byte]], headerPath: Option[String],
                               footer: Option[Array[Byte]], footerPath: Option[String], stylesheet: Array[Byte],
                               previewJson: Array[Byte])

/**
 * Abstract storage for templates.
 * @author Christian
 */
trait TemplateStore {

  /**
   * Insert new template. Returns either Successful, TemplateNameInvalid or TemplateNameAlreadyInUse.
   */
  def insert(name: String, template: TemplateContentData): Try[Unit]

  /**
   * Get list of existing template names.
   */
  def list: Seq[String]

  /**
   * Get template content, or None if template did not exist.
   */
  def get(name: String): Option[TemplateContentData]

  /**
   * Get template content, or None if template did not exist.
   */
  def apply(name: String): Option[TemplateContentData] = get(name)

  /**
   * Retrieve template content.
   */
  def retrieve(name: String): Try[TemplateContentData]

  /**
   * Remove template. Returns either Successful or TemplateNotExisting.
   */
  def remove(name: String): Try[Unit]

  /**
   * Replace template content. Returns either Successful or TemplateNotExisting.
   */
  def replace(name: String, template: TemplateContentData): Try[Unit]

}

object TemplateStore {

  /**
    * Result type trait for template operations.
    */
  sealed trait TemplateOperationResult extends ApiMessage

  case object Successful extends TemplateOperationResult
  case class SuccessfulWithValue(value: Any) extends TemplateOperationResult
  case class OperationFailed(thr: Throwable) extends Exception(thr)

  def TemplateNameInvalid(name: String): OperationFailureMessage =
    OperationFailureMessage(s"Template name '$name' is invalid", INVALID_TEMPLATE_NAME)
  def TemplateNotExisting(name: String): OperationFailureMessage =
    OperationFailureMessage(s"Template with name '$name' does not exist", TEMPLATE_NOT_EXISTING)
  def TemplateNameAlreadyInUse(name: String): OperationFailureMessage =
    OperationFailureMessage(s"Template with name '$name' already exists", TEMPLATE_NAME_ALREADY_IN_USE)
}


