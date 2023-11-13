package com.itizzimo.pdfplugin.actor

import java.io.{File, PrintWriter, StringWriter}
import java.nio.charset.StandardCharsets.UTF_8

import akka.actor.actorRef2Scala
import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfGenerationJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.SubTaskException
import com.itizzimo.pdfplugin.actor.StepFetchTemplateData.TemplateData
import com.itizzimo.pdfplugin.helpers.FileSystemHelper
import com.roundeights.hasher.Implicits._
import io.github.simplifier_ag.scala.spdf.Parameter
import org.apache.commons.io.FileUtils._
import org.fusesource.scalate.{DefaultRenderContext, Template, TemplateEngine}

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap


/**
  * PDF Generation step: evaluate template data.
  *
  * @author Christian Simon
  */
class StepEvaluateData(fileSystemHelper: FileSystemHelper) extends PdfGenerationActor {

  import StepEvaluateData._

  val templateEngine = new TemplateEngine

  private[this] val compilationCache: concurrent.Map[String, Template] = TrieMap.empty

  override def receive: PartialFunction[Any, Unit] = {
    case TemplateData(job, data) => try {
      val evaluatedTemplate = evaluateData(job, data)
      sender ! EvaluatedTemplate(job, evaluatedTemplate)
    } catch {
      case exc: Exception => sender ! SubTaskException(exc)
    }
  }

  private def evaluateData[T <: PdfGenerationJobAbstraction](job: T, data: Map[String, Any]): Content = {
    log.debug(s"[Job ${job.jobId}] Evaluating template data ...")
    val templateContent = new String(job.templateData, UTF_8)
    val template = getOrCompile(job.jobId, templateContent) {
      _ =>
        templateEngine.compileMoustache(templateContent, None)
    }
    val header = if(job.hasCustomHeader)
      renderHeaderOrFooter(job.pdfConfig.headerHtml, job.jobId, "header", data)
    else None

    val footer = if(job.hasCustomFooter)
      renderHeaderOrFooter(job.pdfConfig.footerHtml, job.jobId, "footer", data)
    else None

    val buffer = new StringWriter
    val context = new DefaultRenderContext("", templateEngine, new PrintWriter(buffer))
    data.keys.foreach {
      key => context.setAttribute(key, Some(data(key)))
    }
    template.render(context)
    Content(buffer.toString, header, footer)
  }

  def getOrCompile(jobId: String, source: String)(compile: String => Template): Template = {
    val hash = source.getBytes(UTF_8).sha256.hex
    compilationCache.get(hash) match {
      case Some(template) =>
        log.debug(s"[Job $jobId] Taking compiled template from cache ...")
        template
      case _ =>
        log.debug(s"[Job $jobId] Compiling template ...")
        val template = compile(source)
        compilationCache += hash -> template
        template
    }
  }

  /*
  * If version number of wkhtmltopdf is bigger than 0.12.3, the additional parameter "enable-local-file-access"
  * has to be added in order to include the local stylesheet file
   */
  private def renderHeaderOrFooter[T <: PdfGenerationJobAbstraction](parameter: Parameter[String], jobId: String,
                                                                     name: String, data: Map[String, Any]): Option[String] = {
    if (parameter.toParameter.nonEmpty) {
      val buffer = new StringWriter
      val file = new File(parameter.toParameter.toSeq(1))
      val context = new DefaultRenderContext("", templateEngine, new PrintWriter(buffer))
      data.keys.foreach(key => context.setAttribute(key, Some(data(key))))
      templateEngine.compileMoustache(readFileToString(file, UTF_8), None).render(context)
      Some(buffer.toString)
    } else {
      None
    }
  }

}

object StepEvaluateData {

  /**
    * Result object of the step EvaluateData.
    *
    * @param job     initial generation job
    * @param content HTML content as String
    */
  case class EvaluatedTemplate(job: PdfGenerationJobAbstraction, content: Content)

  case class Content(bodyText: String, headerText: Option[String], footerText: Option[String])

}