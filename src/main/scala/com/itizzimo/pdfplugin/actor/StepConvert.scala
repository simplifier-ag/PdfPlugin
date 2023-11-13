package com.itizzimo.pdfplugin.actor

import java.io.{ByteArrayOutputStream, File}

import akka.actor.actorRef2Scala
import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.{DirectGenerationJob, SubTaskException}
import com.itizzimo.pdfplugin.actor.StepPrepareFiles.PreparationResult
import com.typesafe.config.Config
import io.github.simplifier_ag.scala.spdf.{Pdf, PdfConfig}
import org.apache.commons.io.FileUtils

/**
  * PDF Generation step: convert HTML file with assets to PDF.
  *
  * @author Christian Simon
  */
class StepConvert(config: Config) extends PdfGenerationActor {

  import StepConvert._

  val pathToWkHtmlToPdf: String = config.getString("pdfPlugin.wkhtmltopdf")

  override def receive: PartialFunction[Any, Unit] = {
    case PreparationResult(job, tempDirPath, templatePath) => try {
      val pdfData = convertTemplate(job, tempDirPath, templatePath)
      sender ! ConvertResult(job, pdfData)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
    case job: DirectGenerationJob => try {
      val pdfData = convertString(job.jobId, job.htmlString, job.pdfConfig)
      sender ! ConvertResult(job, pdfData)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def convertString(jobId: String, htmlString: String, pdfConfig: PdfConfig): Array[Byte] = {
    log.debug(s"[Job $jobId] Converting HTML string to PDF ...")
    val buffer = new ByteArrayOutputStream
    val pdf = {
      Thread.sleep(2000)
      Pdf(pathToWkHtmlToPdf, pdfConfig)
    }
    pdf.run(htmlString, buffer)
    buffer.toByteArray
  }

  private def convertTemplate[T <: PdfJobAbstraction](job: T, tempDirPath: String, templatePath: String): Array[Byte] = {
    log.debug(s"[Job ${job.jobId}] Converting template HTML to PDF ...")
    val tempDir = new File(tempDirPath)
    val htmlFile = new File(tempDir, templatePath)
    val pdf = {
      Thread.sleep(2000)

      Pdf(pathToWkHtmlToPdf, job.pdfConfig)
    }
    val buffer = new ByteArrayOutputStream
    pdf.run(htmlFile, buffer)

    if (config getBoolean "pdfPlugin.clearTempFiles") {
      log.debug(s"Removing temporary directory $tempDirPath")
      FileUtils.deleteDirectory(new File(tempDirPath))
    }
    buffer.toByteArray
  }

}

/**
  * Companion object to StepConvert.
  */
object StepConvert {

  /**
    * Result object of the step Convert.
    *
    * @param job     initial generation job
    * @param pdfData binary data of the converted PDF
    */
  case class ConvertResult(job: PdfJobAbstraction, pdfData: Array[Byte])
}