package com.itizzimo.pdfplugin

import io.github.simplifier_ag.scala.spdf.{Parameter, PdfConfig}

class ExtendedPdfConfig(allowJavascript: Boolean) extends PdfConfig {

  val headerParams: Seq[Parameter[String]] = Seq(headerCenter,
    headerFontName,
    headerFontSize,
    headerHtml,
    headerLeft,
    headerRight)

  val footerParams: Seq[Parameter[String]] = Seq(footerCenter,
    footerFontName,
    footerFontSize,
    footerHtml,
    footerLeft,
    footerRight)

  def hasHeaderParams: Boolean = headerParams.exists(param => param.toParameter.nonEmpty)

  def hasFooterParams: Boolean = footerParams.exists(param => param.toParameter.nonEmpty)

  enableJavascript := allowJavascript
  disableJavascript := !allowJavascript

}
