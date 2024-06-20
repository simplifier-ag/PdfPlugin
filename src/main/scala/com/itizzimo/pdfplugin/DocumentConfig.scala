package com.itizzimo.pdfplugin

import com.typesafe.config.Config
import io.simplifier.pluginapi.helper.PluginLogger
import io.github.simplifier_ag.scala.spdf._
import org.json4s._

import scala.util.Try

/**
  * JSON mapping for all support sPDF Configuration options.
  *
  * @author Christian Simon
  */
object DocumentConfig extends PluginLogger {

  /** Mappings */
  sealed trait ParamMapping {
    def param: Parameter[_]

    def name: String = param.name
  }

  case class BooleanParamMapping(param: Parameter[Boolean]) extends ParamMapping

  case class StringParamMapping(param: Parameter[String]) extends ParamMapping

  case class IntParamMapping(param: Parameter[Int]) extends ParamMapping

  case class FloatParamMapping(param: Parameter[Float]) extends ParamMapping

  case class BooleanOptParamMapping(param: Parameter[Option[Boolean]]) extends ParamMapping

  case class PageOrientationParamMapping(param: Parameter[PageOrientation]) extends ParamMapping

  case class ErrorHandlingParamMapping(param: Parameter[ErrorHandling]) extends ParamMapping

  case class LogLevelParamMapping(param: Parameter[LogLevel]) extends ParamMapping

  case class RepeatableStringParamMapping(param: Parameter[Seq[String]]) extends ParamMapping

  case class RepeatableStringMapParamMapping(param: Parameter[Map[String, String]]) extends ParamMapping

  /**
   *  Read feature flag: wkhtmltopdf is allowed to execute server side javascript
   */
  def getJavascriptEnabled(config: Config): Boolean = {
    // plugin-base does not, read config via resolve(), so emulate the following behavior:
    //  use environment variable, fall back to config property and then default value
    val defaultValue = false
    val enabled = sys.env.get("PDFPLUGIN_SECURITY_ALLOW_JAVASCRIPT").map(_.toLowerCase() == "true").getOrElse {
      Try(config.getBoolean("security.allowJavascript")).getOrElse(defaultValue)
    }
    if (enabled) {
      log.warn("Unsafe Javascript execution is currently enabled.")
    }
    enabled
  }

  /**
    * Parse PDF Parameters from JSON value.
    */
  def getPdfConfigFromJSON(json: Option[JValue], allowJavascript: Boolean): ExtendedPdfConfig = json match {
    case None =>
      new ExtendedPdfConfig(allowJavascript) {}
    case Some(obj: JObject) =>
      new ExtendedPdfConfig(allowJavascript) {

        val booleanParams: Seq[Parameter[Boolean]] = Seq(
          extendedHelp,
          grayScale,
          help,
          htmlDoc,
          license,
          lowquality,
          manPage,
          noPdfCompression,
          readArgsFromStdin,
          readme,
          useXServer,
          version,
          dumpDefaultTocXsl,
          defaultHeader,
          disableExternalLinks,
          enableExternalLinks,
          disableForms,
          enableForms,
          disableInternalLinks,
          enableInternalLinks,
          keepRelativeLinks,
          disableLocalFileAccess,
          enableLocalFileAccess,
          excludeFromOutline,
          includeInOutline,
          disablePlugins,
          enablePlugins,
          proxyHostnameLookup,
          resolveRelativeLinks,
          disableSmartShrinking,
          enableSmartShrinking,
          disableTocBacklinks,
          enableTocBacklinks,
          disableDottedLines,
          disableTocLinks,
          tableOfContent)

        val stringParams: Seq[Parameter[String]] = Seq(
          cookieJar,
          marginBottom,
          marginLeft,
          marginRight,
          marginTop,
          pageHeight,
          pageSize,
          pageWidth,
          title,
          dumpOutline,
          cacheDir,
          checkboxCheckedSvg,
          checkboxSvg,
          encoding,
          pageOffset,
          password,
          proxy,
          radioButtonCheckedSvg,
          radioButtonSvg,
          sslCrtParth,
          sslKeyPassword,
          sslKeyPath,
          userStyleSheet,
          username,
          viewportSize,
          windowStatus,
          footerCenter,
          footerFontName,
          footerFontSize,
          footerHtml,
          footerLeft,
          footerRight,
          headerCenter,
          headerFontName,
          headerFontSize,
          headerHtml,
          headerLeft,
          headerRight,
          tocHeaderText,
          tocLevelIndentation,
          xslStyleSheet)

        val intParams: Seq[Parameter[Int]] = Seq(
          copies,
          dpi,
          imageDpi,
          imageQuality,
          outlineDepth,
          javascriptDelay,
          minimumFontSize)

        val floatParams: Seq[Parameter[Float]] = Seq(
          zoom,
          footerSpacing,
          headerSpacing,
          tocTextSizeShrink)

        val booleanOptParams: Seq[Parameter[Option[Boolean]]] = Seq(
          collate,
          outline,
          background,
          customHeaderPropagation,
          debugJavascript,
          images,
          printMediaType,
          stopSlowScripts,
          footerLine,
          headerLine)

        val pageOrientationParams: Seq[Parameter[PageOrientation]] = Seq(
          orientation)

        val errorHandlingParams: Seq[Parameter[ErrorHandling]] = Seq(
          loadErrorHandling,
          loadMediaErrorHandling
        )

        val logLevelParams: Seq[Parameter[LogLevel]] = Seq(
          logLevel
        )

        val repeatableStringParams: Seq[Parameter[Seq[String]]] = Seq(
          allow,
          bypassProxyFor,
          runScript
        )

        val repeatableStringMapParams: Seq[Parameter[Map[String, String]]] = Seq(
          cookie,
          customHeader,
          post,
          postFile,
          replace
        )

        val params: Seq[ParamMapping] =
          booleanParams.map { BooleanParamMapping } ++
            stringParams.map { StringParamMapping } ++
            intParams.map { IntParamMapping } ++
            floatParams.map { FloatParamMapping } ++
            booleanOptParams.map { BooleanOptParamMapping } ++
            pageOrientationParams.map { PageOrientationParamMapping } ++
            errorHandlingParams.map { ErrorHandlingParamMapping } ++
            logLevelParams.map {LogLevelParamMapping } ++
            repeatableStringParams.map { RepeatableStringParamMapping } ++
            repeatableStringMapParams.map { RepeatableStringMapParamMapping }


        val paramsByName: Map[String, ParamMapping] =
          params.map { param => (param.name, param) }.toMap

        def initFromJson(jObj: JObject): Unit = {
          jObj.values.foreach {
            case (name, value) =>
              paramsByName.get(name) match {
                case None => log.warn(s"Configuration setting '$name' not recognized.")
                case Some(mapping) => setParam(mapping, value)
              }
          }
        }

        def setParam(mapping: ParamMapping, value: Any): Unit = mapping match {
          case BooleanParamMapping(param) =>
            value match {
              case bool: Boolean => param := bool
              case _: String if value.toString.toLowerCase.matches("(true|false)") => param := value.toString.toBoolean
              case _ => log.warn(s"Invalid boolean value for param ${param.name}: $value")
            }
          case BooleanOptParamMapping(param) =>
            value match {
              case bool: Boolean => param := Some(bool)
              case _: String if value.toString.toLowerCase.matches("(true|false)") => param := Some(value.toString.toBoolean)
              case _ => log.warn(s"Invalid boolean value for param ${param.name}: $value")
            }
          case StringParamMapping(param) =>
            param := value.toString
          case IntParamMapping(param) =>
            value match {
              case number: Number => param := number.intValue
              case _: String => try {
                param := value.toString.toInt
              } catch {
                case _: NumberFormatException => log.warn(s"Invalid int value for param ${param.name}: $value")
              }
              case _ => log.warn(s"Invalid int value for param ${param.name}: $value")
            }
          case FloatParamMapping(param) =>
            value match {
              case number: Number => param := number.floatValue
              case _: String => try {
                param := value.toString.toFloat
              } catch {
                case _: NumberFormatException => log.warn(s"Invalid float value for param ${param.name}: $value")
              }
              case _ => log.warn(s"Invalid float value for param ${param.name}: $value")
            }
          case PageOrientationParamMapping(param) =>
            if (value.toString.equalsIgnoreCase(Portrait.value))
              param := Portrait
            else if (value.toString.equalsIgnoreCase(Landscape.value))
              param := Landscape
            else {
              log.warn(s"Invalid page orientation value for param ${param.name}: $value")
            }
          case ErrorHandlingParamMapping(param) =>
            value.toString.toLowerCase match {
              case Abort.value =>
                param := Abort
              case Ignore.value =>
                param := Ignore
              case Skip.value =>
                param := Skip
              case _ => log.warn(s"Invalid error handling value for param ${param.name}: $value")
            }
          case LogLevelParamMapping(param) =>
            value.toString.toLowerCase match {
              case none.value =>
                param := none
              case Error.value =>
                param := Error
              case Warn.value =>
                param := Warn
              case Info.value =>
                param := Info
            }
          case RepeatableStringParamMapping(param) =>
            value match {
              case seq: List[Any] if seq.forall(_.isInstanceOf[String]) =>
                param := seq.asInstanceOf[Seq[String]]
              case _ =>
                log.warn(s"Invalid repeatable value for param ${param.name}: $value")
            }
          case RepeatableStringMapParamMapping(param) =>
            value match {
              case map: Any if map.isInstanceOf[Map[Any, Any]] && map.asInstanceOf[Map[Any, Any]].forall {
                case (key, mapVal) =>
                  key.isInstanceOf[String] && mapVal.isInstanceOf[String]
              } => param := map.asInstanceOf[Map[String, String]]
              case _ => log.warn(s"Invalid repeatable tuple value for param ${param.name}: $value")
            }
        }
        // Initialize config from given JSON
        initFromJson(obj)
      }
    case Some(other) =>
      log.warn(s"PDF Configuration expected JSON object but got $other")
      new ExtendedPdfConfig(allowJavascript) {}
  }
}