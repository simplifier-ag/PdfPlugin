package com.itizzimo.pdfplugin.helpers

import java.io.File

import io.simplifier.pluginapi.helper.PluginLogger
import com.itizzimo.pdfplugin.TemplateStore._
import com.itizzimo.pdfplugin.{TemplateContentData, TemplateStore}
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils.{readFileToByteArray, writeByteArrayToFile}
import org.apache.commons.io.FilenameUtils.{getBaseName, getExtension}

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
 * Implementation of TemplateStore, storing templates as files in a directory.
 * @author Christian Simon
 */
class DirectoryTemplateStore(config: Config) extends TemplateStore with PluginLogger {

  private val templateExtension = "html"

  private val stylesheetExtension = "less"

  private val previewJsonExtension = "json"

  private val templatesDir = new File(config getString "pdfPlugin.storageDir")

  /** Pattern for valid template names */
  private val templateNamePattern = """[a-zA-Z0-9_-]+""".r

  case object EmptyResult

  case class ResolvedTemplate(content: File, header: File, footer: File, stylesheet: File, previewJSON: File)

  init()

  /** List all template files. */
  private def listTemplates: Seq[File] = templatesDir.listFiles().seq.filter { file => getExtension(file.getName) == templateExtension && (!file.getName.contains(".header.") && !file.getName.contains(".footer.")) }

  /** Initialize. */
  private def init() = {
    log.debug(s"Using template directory ${templatesDir.getAbsolutePath}")
    if (!templatesDir.exists) {
      log.info(s"Creating template directory ${templatesDir.getAbsolutePath}")
      templatesDir.mkdirs()
    }
  }

  /**
   * Resolve template name to file.
   * @param name template name
   * @return Try containing either successfully resolved file or Error result
   */
  private def resolveTemplate(name: String): Try[ResolvedTemplate] = Try {
    if (templateNamePattern.pattern.matcher(name).matches)
      ResolvedTemplate(
        new File(templatesDir, s"$name.$templateExtension"),
        new File(templatesDir, s"$name.header.$templateExtension"),
        new File(templatesDir, s"$name.footer.$templateExtension"),
        new File(templatesDir, s"$name.$stylesheetExtension"),
        new File(templatesDir, s"$name.$previewJsonExtension")
      )
    else
      throw TemplateNameInvalid(name)
  }

  /**
   * Resolve template name to existing file. If the template does not exist, an error result is returned.
   * @param name template name
   * @return Try containing either successfully resolved file or Error result
   */
  private def resolveExistingTemplate(name: String): Try[ResolvedTemplate] =
    resolveTemplate(name) flatMap {
      tmpl =>
        if (tmpl.content.exists)
          Success(tmpl)
        else
          Failure(TemplateNotExisting(name))
    }

  /**
   * Resolve template name to not existing file. If the template already exists, an error result is returned.
   * @param name template name
   * @return Try containing either successfully resolved file or Error result
   */
  private def resolveNotExistingTemplate(name: String): Try[ResolvedTemplate] =
    resolveTemplate(name) flatMap {
      tmpl =>
        if (!tmpl.content.exists())
          Success(tmpl)
        else
          Failure(TemplateNameAlreadyInUse(name))
    }

  override def list: Seq[String] = {
    val list = listTemplates map { file => getBaseName(file.getName) }
    log.debug("Operation Successful: List Templates")
    list
  }

  override def get(name: String): Option[TemplateContentData] = retrieve(name).toOption

  override def retrieve(name: String): Try[TemplateContentData] = resolveExistingTemplate(name).flatMap {
    tmpl =>
      val data = readFileToByteArray(tmpl.content)
      val (header, headerPath) = if(tmpl.header.exists) {
        (Some(readFileToByteArray(tmpl.header)), Some(tmpl.header.getCanonicalPath))
      } else {
        (None, None)
      }
      val (footer, footerPath) = if(tmpl.footer.exists) {
        (Some(readFileToByteArray(tmpl.footer)), Some(tmpl.footer.getCanonicalPath))
      } else {
        (None, None)
      }
      val stylesheet = if (tmpl.stylesheet.exists) readFileToByteArray(tmpl.stylesheet) else Array[Byte]()
      val previewJson = if (tmpl.previewJSON.exists) readFileToByteArray(tmpl.previewJSON) else Array[Byte]()
      log.debug(s"Operation Successful: Retrieve Template (name=$name)")
      Success(TemplateContentData(data, header, headerPath, footer, footerPath, stylesheet, previewJson))
  }

  override def insert(name: String, template: TemplateContentData): Try[Unit] = resolveNotExistingTemplate(name) flatMap {
    tmpl =>
      writeByteArrayToFile(tmpl.content, template.content)
      template.header.foreach(writeByteArrayToFile(tmpl.header, _))
      template.footer.foreach(writeByteArrayToFile(tmpl.footer, _))
      writeByteArrayToFile(tmpl.stylesheet, template.stylesheet)
      writeByteArrayToFile(tmpl.previewJSON, template.previewJson)
      log.debug(s"Operation Successful: Insert Template (name=$name)")
      Success(())
  }

  override def remove(name: String): Try[Unit] = resolveExistingTemplate(name) flatMap {
    tmpl =>
      tmpl.content.delete()
      tmpl.header.delete()
      tmpl.footer.delete()
      tmpl.stylesheet.delete()
      tmpl.previewJSON.delete()
      log.debug(s"Operation Successful: Delete Template (name=$name)")
      Success(())
  }

  override def replace(name: String, template: TemplateContentData): Try[Unit] = resolveExistingTemplate(name) flatMap {
    tmpl =>
      writeByteArrayToFile(tmpl.content, template.content)
      template.header match {
        case Some(header) => writeByteArrayToFile(tmpl.header, header)
        case None => tmpl.header.delete()
      }
      template.footer match {
        case Some(footer) => writeByteArrayToFile(tmpl.footer, footer)
        case None => tmpl.footer.delete()
      }
      writeByteArrayToFile(tmpl.stylesheet, template.stylesheet)
      writeByteArrayToFile(tmpl.previewJSON, template.previewJson)
      log.debug(s"Operation Successful: Replace Template (name=$name)")
      Success(())
  }

}