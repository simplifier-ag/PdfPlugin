package com.itizzimo.pdfplugin.helpers

import java.nio.charset.StandardCharsets.UTF_8

import io.simplifier.pluginapi.helper.{Base64Encoding, PluginLogger}
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginApiMessage
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.helpers.PluginCommunication
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import io.simplifier.pluginbase.util.json.JSONFormatter._
import org.json4s._
import scala.util.Try
import io.simplifier.pluginbase.util.json.JSONCompatibility._

class KeyValueStoreCommunication(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings)
  extends PluginCommunication(appServerDispatcher, pluginSettings) with Base64Encoding with PluginLogger {

  import KeyValueStoreCommunication._

  val pluginName = "keyValueStorePlugin"

  private def kvKeyForPdf(jobId: String) = s"pdf/$jobId.pdf"

  private def kvKeyForTemplateData(session: String) = s"sessiondata/$session"

  private def kvKeyForMergeResources(session: String) = s"merge/$session"

  def getSessionData(session: String)(implicit requestSource: RequestSource, userSession: UserSession): Option[Map[String, Any]] = {
    val key = kvKeyForTemplateData(session)
    val result = get(key)
    if (result.success) {
       parseJsonOrEmptyString(new String(decodeB64(result.result.get), UTF_8)).toOption match {
        case Some(jObj: JObject) => Some(jObj.values)
        case Some(json) =>
          log.warn(s"Returned data is no JSON Object: $json")
          None
        case None =>
          log.warn("Returned data could not be parsed as JSON")
          None
      }
    } else {
      log.warn(s"Error retrieving session data with key $key: ${result.result}")
      None
    }
  }

  def getMergeResources(session: String)(implicit requestSource: RequestSource, userSession: UserSession): Option[List[Array[Byte]]] = {
    val key = kvKeyForMergeResources(session)
    val result = get(key)
    (if (result.success) {
      (parseJsonOrEmptyString(new String(decodeB64(result.result.get), UTF_8)).toOption match {
        case Some(jsonArray: JArray) =>
          Some(jsonArray.children.filter {
            _.isInstanceOf[JString]
          }.map {
            _.asInstanceOf[JString].values
          })
        case Some(json) =>
          log.warn(s"Returned data is in incompatible format: $json")
          None
        case None =>
          log.warn("Returned data could not be parsed as JSON")
          None
      }) map {
        _ map {
          itemKey =>
            val res = get(itemKey)
            if (res.success) {
              Some(decodeB64(res.result.get))
            } else {
              log.info(s"Error retrieving merge resource with key $key, ${res.result}")
              None
            }
        }
      }
    } else {
      log.info(s"Error retrieving merge resources with key $key: ${result.result}")
      None
    }).map(_.flatten)


  }

  def storeToKvStore(jobId: String, data: Array[Byte])(implicit requestSource: RequestSource, userSession: UserSession): Try[KeyValueStoreResult] = {
    put(kvKeyForPdf(jobId), data)
  }

  private def get(key: String)(implicit requestSource: RequestSource, userSession: UserSession): KeyValueStoreResult = {
    callPlugin[KeyValueStoreResult, KeyValueStoreGetRequest]("get", KeyValueStoreGetRequest(key), None)
  }

  def put(key: String, data: Array[Byte])(implicit requestSource: RequestSource, userSession: UserSession): Try[KeyValueStoreResult] = Try {
    callPlugin[KeyValueStoreResult, KeyValueStorePutRequest]("put", KeyValueStorePutRequest(key, Some(encodeB64(data))), None)
  }

}

object KeyValueStoreCommunication {

  def apply(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings): KeyValueStoreCommunication =
    new KeyValueStoreCommunication(appServerDispatcher, pluginSettings)

  case class KeyValueStoreGetRequest(key: String) extends PluginApiMessage

  case class KeyValueStorePutRequest(key: String, content: Option[String]) extends PluginApiMessage

  case class KeyValueStoreResult(result: Option[String], message: Option[String], success: Boolean)

}
