package com.itizzimo.pdfplugin.controller

import io.simplifier.pluginapi.helper.Base64Encoding
import io.simplifier.pluginbase.util.api.ApiMessage
import io.simplifier.pluginbase.util.json.SimplifierFormats
import com.itizzimo.pdfplugin.RestMessages._
import com.itizzimo.pdfplugin.TemplateContentData
import com.itizzimo.pdfplugin.helpers.DirectoryTemplateStore
import com.itizzimo.pdfplugin.permission.PdfPluginPermission.{characteristicManageTemplates, characteristicView}
import com.itizzimo.pdfplugin.permission.PermissionHandler
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.slotservice.GenericRestMessages.RestMessage

import scala.util.{Failure, Success, Try}

class AdministrationController(templateStore: DirectoryTemplateStore,
                               permissionHandler: PermissionHandler) extends Base64Encoding with SimplifierFormats {

  import AdministrationController._

  def addTemplate(request: HttpParamTemplateAdd)(implicit userSession: UserSession,
                                                 requestSource: RequestSource): Try[Response] = {
    permissionHandler.checkPermission(characteristicManageTemplates)
    templateStore.insert(request.name, TemplateContentData(request.dataDecoded, request.headerDecoded, None,
      request.footerDecoded, None, request.stylesheetDecoded, request.previewJsonDecoded)) map { _ =>
      Response(addTemplateSuccess)
    }
  }

  def deleteTemplate(request: DeleteTemplateRequest)(implicit userSession: UserSession,
                                                     requestSource: RequestSource): Try[Response] = {
    permissionHandler.checkPermission(characteristicManageTemplates)
    templateStore.remove(request.name) map { _ =>
      Response(deleteTemplateSuccess)
    }
  }

  def editTemplate(arg: EditTemplateRequest)(implicit userSession: UserSession, requestSource: RequestSource): Try[Response] = {
    permissionHandler.checkPermission(characteristicManageTemplates)
    templateStore.replace(arg.name, TemplateContentData(arg.dataDecoded, arg.headerDecoded, None,
      arg.footerDecoded, None, arg.stylesheetDecoded, arg.previewJsonDecoded)) map { _ =>
      Response(editTemplateSuccess)
    }
  }

  def fetchTemplate(arg: FetchTemplateRequest)(implicit userSession: UserSession, requestSource: RequestSource): Try[FetchTemplateResponse] = Try {
    permissionHandler.checkAdditionalPermission(characteristicView)
    templateStore.retrieve(arg.name) match {
      case Success(TemplateContentData(template, header, _, footer, _, stylesheet, previewJson)) =>
        FetchTemplateResponse(EncodedTemplateContentData(encodeB64(template), header map encodeB64, footer map encodeB64,
          encodeB64(stylesheet), encodeB64(previewJson)), fetchTemplateSuccess)
      case Failure(ex) => throw ex
    }
  }

  def listTemplates()(implicit userSession: UserSession, requestSource: RequestSource): Try[ListTemplatesResponse] = Try {
    permissionHandler.checkAdditionalPermission(characteristicView)
    ListTemplatesResponse(templateStore.list, listTemplatesSuccess)
  }

}

object AdministrationController {

  trait DataRequest extends Base64Encoding {
    val name: String
    val data: String
    val header: Option[String]
    val footer: Option[String]
    val stylesheet: Option[String]
    val previewJson: Option[String]

    /**
      * Decode data value.
      */
    def dataDecoded: Array[Byte] = decodeB64(data)

    def headerDecoded: Option[Array[Byte]] = decodeOpt(header)

    def footerDecoded: Option[Array[Byte]] = decodeOpt(footer)

    /**
      * Decode stylesheet value.
      */
    def stylesheetDecoded: Array[Byte] = decodeFromOpt(stylesheet)

    /**
      * Decode previewJson value.
      */
    def previewJsonDecoded: Array[Byte] = decodeFromOpt(previewJson)

    private def decodeFromOpt(input: Option[String]): Array[Byte] = input map decodeB64 getOrElse Array[Byte]()

    private def decodeOpt(input: Option[String]): Option[Array[Byte]] = input flatMap { in =>
      if (in.length == 0)
        None
      else
        Some(decodeB64(in))
    }

  }

  /**
    * Parameter class for adminTemplateDelete slot.
    *
    * @param name        template name
    * @param data        template data encoded as Base64
    * @param stylesheet  optional stylesheet data encoded as Base64
    * @param previewJson optional preview JSON data encoded as Base64
    */
  case class HttpParamTemplateAdd(name: String, data: String, header: Option[String], footer: Option[String],
                                  stylesheet: Option[String], previewJson: Option[String]) extends DataRequest with ApiMessage

  /**
    * Parameter class for adminTemplateDelete slot.
    *
    * @param name template name
    */
  case class DeleteTemplateRequest(name: String) extends ApiMessage


  /**
    * Parameter class for adminTemplateFetch slot.
    *
    * @param name        template name
    * @param data        template data encoded as Base64
    * @param stylesheet  optional stylesheet data encoded as Base64
    * @param previewJson optional preview JSON data encoded as Base64
    */
  case class EditTemplateRequest(name: String, data: String, header: Option[String], footer: Option[String],
                                 stylesheet: Option[String], previewJson: Option[String]) extends DataRequest with ApiMessage

  /**
    * Parameter class for adminTemplateFetch slot.
    *
    * @param name template name
    */
  case class FetchTemplateRequest(name: String) extends ApiMessage

  /**
    * Holder for Base64 encoded values from TemplateContentData.
    */
  case class EncodedTemplateContentData(template: String, header: Option[String], footer: Option[String],
                                        stylesheet: String, previewJson: String)

  case class ListTemplatesResponse(templates: Seq[String], message: RestMessage, success: Boolean = true) extends ApiMessage

  case class FetchTemplateResponse(template: EncodedTemplateContentData, message: RestMessage, success: Boolean = true) extends ApiMessage

  case class Response(message: RestMessage, success: Boolean = true) extends ApiMessage

}