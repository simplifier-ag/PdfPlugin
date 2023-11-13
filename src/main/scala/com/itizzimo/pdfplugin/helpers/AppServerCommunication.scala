package com.itizzimo.pdfplugin.helpers

import akka.stream.Materializer
import akka.util.ByteString
import io.simplifier.pluginapi.helper.{Base64Encoding, PluginLogger}
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import org.json4s._

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
  * Abstraction for Communication with AppServer.
  *
  * @author Christian Simon
  */
class AppServerCommunication(keyValueStoreCommunication: KeyValueStoreCommunication,
                             appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings)
                            (implicit materializer: Materializer) extends Base64Encoding with PluginLogger {

  import AppServerCommunication._

  type BinaryData = Array[Byte]

  private def kvKeyForError(jobId: String) = s"pdf/$jobId.log"

  /**
    * Retrieve an asset from the AppServer.
    *
    * @param filename filename of the asset
    * @return Some asset data if successfully found, None otherwise
    */
  def getAsset(filename: String)(implicit requestSource: RequestSource): Try[Array[Byte]] = Try {
    implicit val userSession: UserSession = UserSession.unauthenticated

    val request = AssetRequest(filename)
    val downloadedAsset = Await.result(appServerDispatcher.downloadAsset(request.name), pluginSettings.timeout.duration)

    Await.result(downloadedAsset.runFold(ByteString.empty)(_ ++ _).map(_.toArray[Byte]), pluginSettings.timeout.duration)
  }

  /**
    * Store generation error log in Key-Value-Store.
    *
    * @param jobId PDF generation job ID
    * @param error error message
    */
  def logError(jobId: String, error: String)(implicit requestSource: RequestSource, userSession: UserSession): Unit = {
    val key = kvKeyForError(jobId)

    keyValueStoreCommunication.put(key, error.getBytes(UTF_8)) match {
      case Success(_) =>
      case Failure(exception) => log.error("Error storing error log to KeyValue Store", exception)
    }
    if (error.startsWith("throwMe")) throw new Exception(s"${error.replaceFirst("throwMe:", "")}")
  }

}

object AppServerCommunication {

  implicit val formats: Formats = DefaultFormats

  case class AssetRequest(name: String)

  case class AssetResponse(found: Boolean, data: Option[String])

  case class AppServerCommunicationException(msg: String) extends Exception(msg)

  object ExtractAssetResult extends ExtractJsonResponse[AssetResponse]

  /**
    * Extractor class to extract values from JSON in case statements.
    */
  class ExtractJsonResponse[A](implicit manifest: Manifest[A]) {
    def unapply(json: JValue): Option[A] = Extraction.extractOpt(json)
  }

}