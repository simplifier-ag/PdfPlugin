package com.itizzimo.pdfplugin.actor

import akka.actor.Actor
import io.simplifier.pluginapi.helper.PluginLogger

/**
 * Abstract actor modeling a step for the PDF generation.
 * @author Christian Simon
 */
trait PdfGenerationActor extends Actor with PluginLogger {

  override def unhandled(message: Any): Unit =
    log.warn(s"Received unhandled message $message")

}