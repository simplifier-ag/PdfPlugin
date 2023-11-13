package com.itizzimo.pdfplugin.actor

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException}

import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfCreationJob
import com.itizzimo.pdfplugin.actor.ProcessGeneration.SubTaskException
import com.itizzimo.pdfplugin.helpers.AppServerCommunication
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.xobject.PDJpeg
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.apache.pdfbox.util.PDFMergerUtility

import scala.util.{Failure, Success}

/**
  * Created by b001 on 08.08.16.
  * 08.08.2016 10:14
  */
class StepCreatePdfWithContent(appServerCommunication: AppServerCommunication) extends PdfGenerationActor {

  import StepCreatePdfWithContent._

  override def receive: PartialFunction[Any, Unit] = {
    case job: PdfCreationJob => try {
      val PdfCreationJob(jobId, sessions, _, _, _, _, _) = job
      val mergedData = createPdfWithContent(jobId, sessions)(job.requestSource)
      sender ! CreatedPdf(job, mergedData)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def createPdfWithContent(jobId: String, sessions: Seq[String])(implicit requestSource: RequestSource): Array[Byte] = {
    log.debug(s"[Job $jobId] Merging PDF with resources ...")
      if (sessions.isEmpty) {
        log.warn("No session sent to create a PDF.")
        Array[Byte]()
        // TODO: THROW ERROR?
      } else {
        val appendices = sessions map { session =>
          getResourceData(session)
        } map { makePdf } filter { _.isDefined } map { _.get }

        val merger = new PDFMergerUtility
        appendices.foreach { data => merger.addSource(new ByteArrayInputStream(data)) }
        val buffer = new ByteArrayOutputStream

        merger.setDestinationStream(buffer)
        merger.mergeDocuments()
        buffer.toByteArray
      }
  }

  private def getResourceData(session: String)(implicit requestSource: RequestSource): Array[Byte] = {
    appServerCommunication.getAsset(session) match {
      case Failure(ex) =>
        log.warn("Error getting resources to merge from AppServer - using empty byte array instead.", ex)
        log.warn(s"Affected session '$session'.")
        Array[Byte]()
        // TODO: Throw error?
      case Success(data) =>
        log.debug(s"Found ${data.length} resources to merge.")
        data
    }
  }

  /**
    * Try to parse binary data as PDF. If the data resemble a valid PDF document, parse and return this.
    * If the data is an image, create a PDF from the image and return this PDF. Otherwise return None.
    *
    * @param data binary data of the resource
    * @return Some pdf data if it can be interpreted/converted as PDF, None otherwise
    */
  private def makePdf(data: Array[Byte]): Option[Array[Byte]] = {
    if (data.length > 0) {
      val doc: PDDocument = PDDocument.load(new ByteArrayInputStream(data))
      val buffer = new ByteArrayOutputStream
      try {
        doc.save(buffer)
        Some(buffer.toByteArray)
      } catch {
        case _: IOException =>
          log.warn("Given merge resource is no valid PDF - try to parse it as image")
          val result = new PDDocument
          val buffer = new ByteArrayOutputStream
          try {
            val image = ImageIO.read(new ByteArrayInputStream(data))
            val page = new PDPage(new PDRectangle(image.getWidth, image.getHeight))
            result.addPage(page)
            val pdImage = new PDJpeg(result, image)
            val contentStream = new PDPageContentStream(result, page)
            contentStream.drawImage(pdImage, 0, 0)
            contentStream.close()
            result.save(buffer)
            Some(buffer.toByteArray)
          } catch {
            case e: Exception =>
              log.warn("Error converting merge resource as image to pdf: ", e)
              None
          }
          finally {
            buffer.close()
            result.close()
          }
      } finally {
        buffer.close()
        doc.close()
      }
    } else {
      None
    }
  }
}

object StepCreatePdfWithContent {

  /**
    * Result object of the step Merge.
    *
    * @param job     initial generation job
    * @param pdfData binary data of merged PDF
    */
  case class CreatedPdf(job: PdfCreationJob, pdfData: Array[Byte])
}
