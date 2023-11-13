package com.itizzimo.pdfplugin.actor

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

import akka.actor.actorRef2Scala
import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfGenerationJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.SubTaskException
import com.itizzimo.pdfplugin.actor.StepEvaluateData.{Content, EvaluatedTemplate}
import com.itizzimo.pdfplugin.actor.StepFetchAssets.TemplateWithAssets
import com.itizzimo.pdfplugin.helpers.{AppServerCommunication, FileSystemHelper}
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import org.apache.commons.io.FileUtils.writeStringToFile
import org.apache.commons.io.FilenameUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.lesscss.{LessCompiler, LessException}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}


/**
 * PDF Generation step: Fetch required assets from AppServer.
 *
 * @author Christian Simon
 */
class StepFetchAssets(appServerCommunication: AppServerCommunication, fileSystemHelper: FileSystemHelper)
  extends PdfGenerationActor {

  override def receive: PartialFunction[Any, Unit] = {
    case EvaluatedTemplate(job, content) => try {
      val (contentWithFixedAsset, assets) = fetchAssets(job, content)(job.requestSource)
      sender ! TemplateWithAssets(job, contentWithFixedAsset, assets)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def fetchAssets[T <: PdfGenerationJobAbstraction](job: T, content: Content)
                                                           (implicit requestSource: RequestSource): (String, Map[String, Array[Byte]]) = {
    log.debug(s"[Job ${job.jobId}] Fetching assets ...")
    val tempDir = fileSystemHelper.resolveTempDir(job.jobId)
    val doc = Jsoup parse content.bodyText
    var assets: Map[String, Array[Byte]] = Map()

    // Find Stylesheets
    doc.select("link[href]").asScala.foreach {
      element =>
        assets = downloadAndProcessAsset(assets, element, "href", tempDir)
    }

    // Find Images
    doc.select("img[src]").asScala.foreach {
      element =>
        assets = downloadAndProcessAsset(assets, element, "src", tempDir)
    }

    // Add template-attached LESS data
    assets ++= compileAttachedLess(job, tempDir, content, doc)

    (doc.html, assets)
  }

  /**
    * Try to download asset.
    *
    * @param assets result map of assets
    * @param element JSoup element to process
    * @param attr attribute to process
    * @param tempDir temp directory the job will be processed in later (for absolute file URLs)
    * @return result map of assets, with the one asset added
    */
  private def downloadAndProcessAsset(assets: Map[String, Array[Byte]],
                              element: Element,
                              attr: String,
                              tempDir: File)(implicit requestSource: RequestSource): Map[String, Array[Byte]] = {
    val uri = element.attr(attr)
    // Only process relative URLs
    if (!(uri.toLowerCase startsWith "http:") && !(uri.toLowerCase startsWith "https:")) {
      val filename = FilenameUtils getName uri
      appServerCommunication.getAsset(filename) match {
        case Failure(ex) =>
          log.warn(s"Could not find asset '$filename'.", ex)
          // TODO: Throw error ?
          assets
        case Success(data) =>
          log.debug(s"Found asset '$filename'.")
          val absoluteFilename = new File(tempDir, filename).getAbsolutePath
          val absoluteUri = "file:///" + absoluteFilename
          element.attr(attr, absoluteUri)
          assets + (filename -> data)
      }
    } else {
      assets
    }
  }

  /**
    * Try to parse attached stylesheet as LESS and attach it to assets if successful.
    *
    * @param job the data of this pdf job
    * @param tempDir temp directory the job will be processed in later (for absolute file URLs)
    * @param content content to attach link to implicit stylesheet
    */
  private def compileAttachedLess(job: PdfGenerationJobAbstraction,
                          tempDir: File,
                          content: Content,
                          doc: Document): Option[(String, Array[Byte])] = {
    val stylesheetData = job.stylesheetData
    val templateName = job.templateName
    val header: Option[Document] = content.headerText.map(header => Jsoup parse header)
    val footer: Option[Document] = content.footerText.map(footer => Jsoup parse footer)

    val lessCompiler = new LessCompiler(List("--silent").asJava)
    val lessSource = new String(stylesheetData, "UTF-8")
    try {
      val cssSource = lessCompiler compile lessSource
      val filename = templateName + ".css"
      val absoluteFilename = new File(tempDir, filename).getAbsolutePath
      val absoluteUri = "file:///" + absoluteFilename

      val applyStyleSheet = (doc: Document) => doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", absoluteUri)

      applyStyleSheet(doc)

      header.foreach(h => {
        applyStyleSheet(h)
        val tempFile = new File(fileSystemHelper.mkTempDir(job.jobId), s"temp.header.html")
        writeStringToFile(tempFile, h.html(), UTF_8)
        job.pdfConfig.headerHtml := tempFile.getAbsolutePath
      })

      footer.foreach(f => {
        applyStyleSheet(f)
        val tempFile = new File(fileSystemHelper.mkTempDir(job.jobId), s"temp.footer.html")
        writeStringToFile(tempFile, f.html(), UTF_8)
        job.pdfConfig.footerHtml := tempFile.getAbsolutePath
      })

      Some(filename -> cssSource.getBytes("UTF-8"))
    } catch {
      case e: LessException =>
        log.error("Error compiling LESS: {}", e.getMessage)
        None
    }

  }
}

object StepFetchAssets {

  /**
    * Result object of the step FetchAssets.
    *
    * @param job initial generation job
    * @param content HTML content as String
    * @param assets map of retrieved assets (key is the asset filename, value is the binary data)
    */
  case class TemplateWithAssets(job: PdfGenerationJobAbstraction, content: String, assets: Map[String, Array[Byte]])
}