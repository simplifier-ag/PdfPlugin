package com.itizzimo.pdfplugin

import io.github.simplifier_ag.scala.spdf.ParamShow._
import org.json4s.JObject
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

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

  }
}
