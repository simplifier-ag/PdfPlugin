package com.itizzimo.pdfplugin.slots

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.simplifier.pluginbase.util.api.ApiMessage
import io.simplifier.pluginbase.util.http.JsonMarshalling._
import com.itizzimo.pdfplugin.Constants._
import io.simplifier.pluginbase.slotservice.Constants._
import io.simplifier.pluginbase.slotservice.GenericFailureHandling.{OperationFailure, OperationFailureResponse}
import io.simplifier.pluginbase.slotservice.GenericRestMessages.RestMessage
import io.simplifier.pluginbase.slotservice.GenericSlotService

import io.simplifier.pluginbase.util.json.JSONFormatter._

import org.json4s._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

abstract class GenericPdfSlotService extends GenericSlotService {

  import GenericPdfSlotService._

  override def requestHandler[T <: ApiMessage](function: T => Try[ApiMessage],
                                               error: String => RestMessage,
                                               action: String, aspect: String)(implicit manifest: Manifest[T]): Route ={
    entity(as[T]) { request =>
      val result = resultHandler(function(request), error, action, aspect) match {
        case ofr: OperationFailureResponse => PdfPluginErrorResponse(ofr)
        case res: ApiMessage => res
      }
      complete(result)
    } ~
      entity(as[JValue]) { request =>
        Try {
          request.extract[T]
        } match {
          case Failure(ex) => complete(OperationFailure(error(ex.getMessage), ERROR_CODE_MAPPING_EXCEPTION).toResponse)
          case Success(extractedRequest) => complete(resultHandler(function(extractedRequest), error, action, aspect))
        }
      }
  }

  override def asyncRequestHandler[T <: ApiMessage](function: T => Future[ApiMessage],
                                               error: String => RestMessage,
                                               action: String, aspect: String)(implicit manifest: Manifest[T]): Route ={
    entity(as[T]) { request =>
      onComplete(function(request)) { data =>
        val result = resultHandler(data, error, action, aspect) match {
          case ofr: OperationFailureResponse => PdfPluginErrorResponse(ofr)
          case res: ApiMessage => res
        }
        complete(result)
      }
    } ~
      entity(as[JValue]) { request =>
        Try {
          request.extract[T]
        } match {
          case Failure(ex) => complete(OperationFailure(error(ex.getMessage), ERROR_CODE_MAPPING_EXCEPTION).toResponse)
          case Success(extractedRequest) =>
            onComplete(function(extractedRequest)) { data =>
              complete(resultHandler(data, error, action, aspect))
            }
        }
      }
  }

}

object GenericPdfSlotService {

  case class PdfPluginErrorResponse(code: Int, message: String, errorCode: String,
                                    errorMessage: RestMessage, success: Boolean = false) extends ApiMessage

  object PdfPluginErrorResponse {

    def getCodeAndMessage(errorCode: String): (Int, String) = {
      errorCode match {
        case INVALID_TEMPLATE_NAME => (1, "template name invalid")
        case TEMPLATE_NOT_EXISTING => (2, "template not existing")
        case TEMPLATE_NAME_ALREADY_IN_USE => (3, "template name already in use")
        case MERGE_NOT_POSSIBLE_DUE_TO_ENCRYPTION => (4, "Merge not possible because the source PDF is encrypted, can't append encrypted PDF documents")
        case ERROR_CODE_MISSING_PERMISSION => (403, "forbidden")
        case _ => (500, "Unexpected exception")
      }
    }

    def apply(ofr: OperationFailureResponse): PdfPluginErrorResponse = {
      val (code, message) = getCodeAndMessage(ofr.errorCode)
      PdfPluginErrorResponse(code, message, ofr.errorCode, ofr.message)
    }
  }

}
