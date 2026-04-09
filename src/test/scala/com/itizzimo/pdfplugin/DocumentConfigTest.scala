package com.itizzimo.pdfplugin

import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.typesafe.config.ConfigFactory
import io.github.simplifier_ag.scala.spdf.ParamShow._
import org.json4s.JObject
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class DocumentConfigTest extends AnyWordSpecLike with Matchers {

  "DocumentConfig" when {


    "no json config is provided" should {
      "provide default options" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(None, allowJavascript = false)

        pdfConfig.collate.toParameter should be(Nil)
        pdfConfig.cookieJar.toParameter should be(Nil)
        pdfConfig.enableForms.toParameter should be(Nil)
        pdfConfig.enablePlugins.toParameter should be(Nil)

        pdfConfig.hasHeaderParams should be(false)
        pdfConfig.hasFooterParams should be(false)
      }

      "toggle Javascript by internal flag" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(None, allowJavascript = false)
        pdfConfig.enableJavascript.toParameter should be(Nil)
        pdfConfig.disableJavascript.toParameter should be("--disable-javascript" :: Nil)

        val pdfConfigWithJs = DocumentConfig.getPdfConfigFromJSON(None, allowJavascript = true)
        pdfConfigWithJs.enableJavascript.toParameter should be("--enable-javascript" :: Nil)
        pdfConfigWithJs.disableJavascript.toParameter should be(Nil)
      }
    }

    "json options are set" should {

      "use provided options" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"dpi": 2800,
            |"page-width": "400px"
            |}""".stripMargin)), allowJavascript = false)

        pdfConfig.dpi.toParameter should be("--dpi" :: "2800" :: Nil)
        pdfConfig.pageWidth.toParameter should be("--page-width" :: "400px" :: Nil)

        pdfConfig.enableJavascript.toParameter should be(Nil)
        pdfConfig.disableJavascript.toParameter should be("--disable-javascript" :: Nil)

        pdfConfig.hasHeaderParams should be(false)
        pdfConfig.hasFooterParams should be(false)
      }

      "indicate header params" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"header-html": "<b>Header</b>"
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.headerHtml.toParameter should be("--header-html" :: "<b>Header</b>" :: Nil)
        pdfConfig.hasHeaderParams should be(true)
        pdfConfig.hasFooterParams should be(false)
      }

      "indicate footer params" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"footer-html": "<b>done</b>"
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.footerHtml.toParameter should be("--footer-html" :: "<b>done</b>" :: Nil)
        pdfConfig.hasHeaderParams should be(false)
        pdfConfig.hasFooterParams should be(true)
      }

      "toggle Javascript by internal flag" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(JObject()), allowJavascript = false)
        pdfConfig.enableJavascript.toParameter should be(Nil)
        pdfConfig.disableJavascript.toParameter should be("--disable-javascript" :: Nil)

        val pdfConfigWithJs = DocumentConfig.getPdfConfigFromJSON(Some(JObject()), allowJavascript = true)
        pdfConfigWithJs.enableJavascript.toParameter should be("--enable-javascript" :: Nil)
        pdfConfigWithJs.disableJavascript.toParameter should be(Nil)
      }

      "override Javascript settings by internal flag" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"enable-javascript": true
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.enableJavascript.toParameter should be(Nil)
        pdfConfig.disableJavascript.toParameter should be("--disable-javascript" :: Nil)

        val pdfConfigWithJs = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"enable-javascript": false
            |}""".stripMargin)), allowJavascript = true)
        pdfConfigWithJs.enableJavascript.toParameter should be("--enable-javascript" :: Nil)
        pdfConfigWithJs.disableJavascript.toParameter should be(Nil)
      }

    }

    "sanitizing config parameters" should {

      "drop blocklisted key regardless of its value" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"user-style-sheet": "plain-value"
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.userStyleSheet.toParameter should be(Nil)
      }

      "drop parameter whose string value is an http URL" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"title": "http://evil.example.com/page"
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.title.toParameter should be(Nil)
      }

      "drop parameter whose string value is an absolute filesystem path" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"title": "/etc/passwd"
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.title.toParameter should be(Nil)
      }

      "apply non-blocked parameters with safe scalar values" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"page-size": "A4",
            |"margin-top": "10mm"
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.pageSize.toParameter should be("--page-size" :: "A4" :: Nil)
        pdfConfig.marginTop.toParameter should be("--margin-top" :: "10mm" :: Nil)
      }

      "emit WARN log entries for dropped parameters" in {
        val loggerName = "com.itizzimo.pdfplugin.DocumentConfig"
        val logger = LoggerFactory.getLogger(loggerName).asInstanceOf[Logger]
        val appender = new ListAppender[ILoggingEvent]()
        appender.start()
        logger.addAppender(appender)
        try {
          DocumentConfig.getPdfConfigFromJSON(Some(parse(
            """{
              |"proxy": "http://evil.example.com",
              |"title": "https://evil.example.com"
              |}""".stripMargin)), allowJavascript = false)

          val warnMessages = appender.list.asScala.filter(_.getLevel == Level.WARN).map(_.getMessage)
          warnMessages.exists(_.contains("proxy")) should be(true)
          warnMessages.exists(_.contains("title")) should be(true)
        } finally {
          logger.detachAppender(appender)
        }
      }

    }

    "getProxyFromConfig" should {

      "return the configured proxy address from config" in {
        val config = ConfigFactory.parseString("""security.proxy = "http://proxy.example.com:3128"""")
        DocumentConfig.getProxyFromConfig(config) should be("http://proxy.example.com:3128")
      }

      "return localhost:65535 as default when no proxy is configured" in {
        val config = ConfigFactory.empty()
        DocumentConfig.getProxyFromConfig(config) should be("localhost:65535")
      }

    }

    "proxy param in caller JSON" should {

      "be ignored and not set on the PdfConfig" in {
        val pdfConfig = DocumentConfig.getPdfConfigFromJSON(Some(parse(
          """{
            |"proxy": "http://caller.example.com:8080"
            |}""".stripMargin)), allowJavascript = false)
        pdfConfig.proxy.toParameter should be(Nil)
      }

    }

    "isUrlOrPath helper" should {

      "detect http URLs" in {
        DocumentConfig.isUrlOrPath("http://example.com") should be(true)
        DocumentConfig.isUrlOrPath("https://example.com/path") should be(true)
      }

      "detect ftp and file URLs" in {
        DocumentConfig.isUrlOrPath("ftp://files.example.com") should be(true)
        DocumentConfig.isUrlOrPath("file:///etc/passwd") should be(true)
      }

      "detect data URIs" in {
        DocumentConfig.isUrlOrPath("data:text/html;base64,abc") should be(true)
      }

      "detect absolute and relative filesystem paths" in {
        DocumentConfig.isUrlOrPath("/etc/passwd") should be(true)
        DocumentConfig.isUrlOrPath("./relative/path") should be(true)
        DocumentConfig.isUrlOrPath("../parent/path") should be(true)
      }

      "detect Windows paths" in {
        DocumentConfig.isUrlOrPath("C:\\Windows\\System32") should be(true)
        DocumentConfig.isUrlOrPath("\\\\server\\share") should be(true)
      }

      "not flag safe scalar values" in {
        DocumentConfig.isUrlOrPath("A4") should be(false)
        DocumentConfig.isUrlOrPath("10mm") should be(false)
        DocumentConfig.isUrlOrPath("Arial") should be(false)
        DocumentConfig.isUrlOrPath("landscape") should be(false)
        DocumentConfig.isUrlOrPath("<b>Header</b>") should be(false)
      }

    }

  }
}
