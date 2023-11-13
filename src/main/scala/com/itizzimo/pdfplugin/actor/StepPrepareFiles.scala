package com.itizzimo.pdfplugin.actor

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

import akka.actor.actorRef2Scala
import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfGenerationJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.SubTaskException
import com.itizzimo.pdfplugin.actor.StepFetchAssets.TemplateWithAssets
import com.itizzimo.pdfplugin.actor.StepPrepareFiles._
import com.itizzimo.pdfplugin.helpers.FileSystemHelper
import org.apache.commons.io.FileUtils.{write, writeByteArrayToFile}


/**
 * PDF Generation step: Create temporary files for conversion.
 *
 * @author Christian Simon
 */
class StepPrepareFiles(fileSystemHelper: FileSystemHelper) extends PdfGenerationActor {

  override def receive: PartialFunction[Any, Unit] = {
    case TemplateWithAssets(job, content, assets) => try {
      val(tempDirPath, templateFileName) = prepareFiles(job.jobId, content, assets)
      sender ! PreparationResult(job, tempDirPath, templateFileName)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def prepareFiles(jobId: String, content: String, assets: Map[String, Array[Byte]]): (String, String) = {
    log.debug(s"[Job $jobId] Preparing files ...")
    val tempDir = fileSystemHelper.mkTempDir(jobId)
    val templateFile = new File(tempDir, "document.html")
    write(templateFile, content, UTF_8)
    assets.keys.foreach {
      assetName =>
        val assetFile = new File(tempDir, assetName)
        writeByteArrayToFile(assetFile, assets(assetName))
    }
    (tempDir.getAbsolutePath, templateFile.getName)
  }
}

/**
 * Companion objecct for static utility functions.
 */
object StepPrepareFiles {

  /**
    * Result object of the step PrepareFiles.
    *
    * @param job initial generation job
    * @param tempDirectory path of temp dir
    * @param templatePath path to template HTML file
    */
  case class PreparationResult(job: PdfGenerationJobAbstraction, tempDirectory: String, templatePath: String)

}
