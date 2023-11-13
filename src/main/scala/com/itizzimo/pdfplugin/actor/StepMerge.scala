package com.itizzimo.pdfplugin.actor

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException}

import io.simplifier.pluginapi.helper.PluginLogger
import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.SubTaskException
import com.itizzimo.pdfplugin.actor.StepFetchMergeResources.PdfWithMergeResources
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.xobject.PDJpeg
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.apache.pdfbox.util.PDFMergerUtility


/**
 * PDF Generation step: Merge PDF with resources.
  *
  * @author Christian Simon
 */
class StepMerge extends PdfGenerationActor {

  import StepMerge._

  override def receive: PartialFunction[Any, Unit] = {
    case PdfWithMergeResources(job, pdfData, mergeResources) => try {
      val mergedData = mergePdf(job.jobId, mergeResources, pdfData)
      sender ! MergedPdf(job, mergedData)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def mergePdf(jobId: String, mergeResources: Seq[Array[Byte]], pdfData: Array[Byte]): Array[Byte] = {
    log.debug(s"[Job $jobId] Merging PDF with resources ...")
    if (mergeResources.isEmpty)
      pdfData
    else {
      val source = makePdf(pdfData) getOrElse { throw new IllegalArgumentException("PDF Data can not be parsed as PDF") }
      val appendices = mergeResources map { makePdf } filter { _.isDefined } map { _.get }
      val merger = new PDFMergerUtility
      merger.addSource(new ByteArrayInputStream(source))
      appendices.foreach { data => merger.addSource(new ByteArrayInputStream(data)) }
      val buffer = new ByteArrayOutputStream
      merger.setDestinationStream(buffer)
      merger.mergeDocuments()
      buffer.toByteArray
    }
  }

  /**
    * Try to parse binary data as PDF. If the data resemble a valid PDF document, parse and return this.
    * If the data is an image, create a PDF from the image and return this PDF. Otherwise return None.
    *
    * @param data binary data of the resource
    * @return Some pdf data if it can be interpreted/converted as PDF, None otherwise
    */
  private def makePdf(data: Array[Byte]): Option[Array[Byte]] =
    try {
      val doc = PDDocument.load(new ByteArrayInputStream(data))
      val buffer = new ByteArrayOutputStream
      doc.save(buffer)
      Some(buffer.toByteArray)
    } catch {
      case _: IOException =>
        log.warn("Given merge resource is no valid PDF - try to parse it as image")
        try {
          val image = ImageIO.read(new ByteArrayInputStream(data))
          val result = new PDDocument
          val page = new PDPage(new PDRectangle(image.getWidth, image.getHeight))
          result.addPage(page)
          val pdImage = new PDJpeg(result, image)
          val contentStream = new PDPageContentStream(result, page)
          contentStream.drawImage(pdImage, 0, 0)
          contentStream.close()
          val buffer = new ByteArrayOutputStream
          result.save(buffer)
          Some(buffer.toByteArray)
        } catch {
          case e: Exception =>
            log.warn("Error converting merge resource as image to pdf: ", e)
            None
        }
    }

}

object StepMerge extends PluginLogger {

  /**
    * Result object of the step Merge.
    *
    * @param job initial generation job
    * @param pdfData binary data of merged PDF
    */
  case class MergedPdf(job: PdfJobAbstraction, pdfData: Array[Byte])

}