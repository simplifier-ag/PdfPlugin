package com.itizzimo.pdfplugin.helpers

import io.simplifier.pluginapi.helper.Base64Encoding
import com.itizzimo.pdfplugin.controller.GenerationController.ContentFileInfo
import com.itizzimo.pdfplugin.helpers.AppServerCommunication.AppServerCommunicationException
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginApiMessage
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.helpers.PluginCommunication
import io.simplifier.pluginbase.interfaces.AppServerDispatcher

class ContentRepoCommunication(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings)
  extends PluginCommunication(appServerDispatcher, pluginSettings) with Base64Encoding {

  import ContentRepoCommunication._

  override val pluginName: String = "contentRepoPlugin"
  val contentFileAddSlot: String = "contentFileAdd"
  val contentFileFindSlot: String = "contentFileFind"
  val contentFileGetSlot: String = "contentFileGet"

  def storeFinishedPdf(contentFileInfo: ContentFileInfo, fileData: Array[Byte])(implicit requestSource: RequestSource, userSession: UserSession): Int = {
    if (fileExistsInFolder(contentFileInfo.folderId, contentFileInfo.fileName)) {
      throw AppServerCommunicationException(s"A file with name '${contentFileInfo.fileName}' already exists in folder with id '${contentFileInfo.folderId}'")
    }

    val request = ContentRepoAddFileRequest(contentFileInfo.folderId, contentFileInfo.fileName, contentFileInfo.fileDescription,
      contentFileInfo.securitySchemeId, contentFileInfo.permissionObjectType, contentFileInfo.permissionObjectId, encodeB64(fileData))

    val response = callPlugin[ContentRepoAddFileResponse, ContentRepoAddFileRequest](contentFileAddSlot, request, None)
    if (response.success) {
      response.id
    } else {
      throw AppServerCommunicationException(s"Failed to store PDF to content repo.")
    }
  }

  def fileExistsInFolder(folderId: Int, fileName: String)(implicit requestSource: RequestSource, userSession: UserSession): Boolean = {
    val request = ContentFileFindRequest(folderId, fileName)

    val response = callPlugin[ContentFileFindResponse, ContentFileFindRequest](contentFileFindSlot, request, None)

    if (response.success) {
        response.files
      } else {
        throw AppServerCommunicationException(s"Failed to find PDF in content repo.")
      }
    } exists (_.folderId == folderId)

  def getFileInFolder(id: Int)(implicit requestSource: RequestSource, userSession: UserSession): ContentFileGetResponse = {
    val request = ContentFileGetRequest(id)
    val response = callPlugin[ContentFileGetResponse, ContentFileGetRequest](contentFileGetSlot, request, None)
    if (!response.success) {
      throw AppServerCommunicationException(s"Failed to find file in content repo.")
    }
    response
  }

}

object ContentRepoCommunication {

  case class ContentRepoAddFileRequest(folderId: Int,
                                       name: String,
                                       description: Option[String],
                                       securitySchemeID: String,
                                       permissionObjectType: String,
                                       permissionObjectID: String,
                                       data: String) extends PluginApiMessage

  case class ContentRepoAddFileResponse(id: Int,
                                        name: String,
                                        success: Boolean) extends PluginApiMessage

  case class ContentFileFindRequest(folderId: Int,
                                    name: String) extends PluginApiMessage

  case class ContentFileGetRequest(id: Int) extends PluginApiMessage

  case class ContentFileFindResponse(files: Seq[ContentFileFindResponseItem], success: Boolean) extends PluginApiMessage

  case class ContentFileFindResponseItem(folderId: Int)

  case class ContentFileGetResponse(data: String, success: Boolean)

}
