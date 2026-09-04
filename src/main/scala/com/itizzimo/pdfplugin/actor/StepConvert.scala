package com.itizzimo.pdfplugin.actor

import com.itizzimo.pdfplugin.actor.ProcessCreation.PdfJobAbstraction
import com.itizzimo.pdfplugin.actor.ProcessGeneration.{DirectGenerationJob, SubTaskException}
import com.itizzimo.pdfplugin.actor.StepPrepareFiles.PreparationResult
import com.typesafe.config.Config
import io.github.simplifier_ag.scala.spdf.{NoExecutableException, PdfConfig}
import io.simplifier.pluginapi.helper.PluginLogger
import org.apache.commons.io.{FileUtils, IOUtils}

import java.io.{ByteArrayOutputStream, File, IOException, InputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit.MILLISECONDS
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try

/**
  * PDF Generation step: convert HTML file with assets to PDF.
  *
  * The wkhtmltopdf process is started directly instead of through sPDF's `Pdf.run`, because that
  * blocks on `Process(...).!` without any timeout: a hanging render would keep the actor's thread
  * and the child process alive indefinitely, long after the surrounding ask has given up.
  *
  * @author Christian Simon
  */
class StepConvert(config: Config, conversionTimeout: FiniteDuration) extends PdfGenerationActor {

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
    Thread.sleep(2000)
    runWkHtmlToPdf(jobId, pdfConfig, StdinSource(htmlString))
  }

  private def convertTemplate[T <: PdfJobAbstraction](job: T, tempDirPath: String, templatePath: String): Array[Byte] = {
    log.debug(s"[Job ${job.jobId}] Converting template HTML to PDF ...")
    val tempDir = new File(tempDirPath)
    val htmlFile = new File(tempDir, templatePath)

    Thread.sleep(2000)

    // from version 0.12.6 of wkhtmltopdf local filesystem access is blocked by default
    // we need to set this parameter to true to be able to access the css style sheet
    job.pdfConfig.enableLocalFileAccess := true

    val pdfData = runWkHtmlToPdf(job.jobId, job.pdfConfig, FileSource(htmlFile))

    if (config getBoolean "pdfPlugin.clearTempFiles") {
      log.debug(s"Removing temporary directory $tempDirPath")
      FileUtils.deleteDirectory(new File(tempDirPath))
    }
    pdfData
  }

  /**
    * Run wkhtmltopdf as a child process and return the generated PDF.
    *
    * The process is given at most `conversionTimeout` to finish; afterwards it is forcibly
    * destroyed and the step fails, so that neither the process nor this actor's thread is held any
    * longer than the surrounding job is willing to wait.
    */
  private def runWkHtmlToPdf(jobId: String, pdfConfig: PdfConfig, source: Source): Array[Byte] = {
    validateExecutable()

    val commandLine = Seq(pathToWkHtmlToPdf, QuietOption) ++ PdfConfig.toParameters(pdfConfig) ++
      Seq(source.commandParameter, StdOutParameter)
    log.debug(s"[Job $jobId] Starting wkhtmltopdf with a timeout of ${conversionTimeout.toSeconds}s ...")

    val process = new ProcessBuilder(commandLine.asJava).start()
    val pdfBuffer = new ByteArrayOutputStream
    val errorBuffer = new ByteArrayOutputStream
    // both pipes must be drained while the process runs, otherwise a full pipe buffer blocks it
    val stdOutPump = pump(s"wkhtmltopdf-stdout-$jobId", process.getInputStream, pdfBuffer)
    val stdErrPump = pump(s"wkhtmltopdf-stderr-$jobId", process.getErrorStream, errorBuffer)

    try {
      writeStdIn(jobId, process, source)

      if (!process.waitFor(conversionTimeout.toMillis, MILLISECONDS)) {
        throw ConversionTimeoutException(jobId, conversionTimeout)
      }
      awaitPump(jobId, stdOutPump)
      awaitPump(jobId, stdErrPump)

      val exitValue = process.exitValue()
      val errorOutput = new String(errorBuffer.toByteArray, UTF_8).trim
      if (exitValue != 0) {
        // wkhtmltopdf also reports a non-zero exit code for recoverable problems (for example a
        // resource that could not be loaded) while still writing a usable PDF, so this is only
        // fatal when no output was produced at all.
        log.warn(s"[Job $jobId] wkhtmltopdf exited with code $exitValue: ${abbreviate(errorOutput)}")
      } else if (errorOutput.nonEmpty) {
        log.debug(s"[Job $jobId] wkhtmltopdf output: ${abbreviate(errorOutput)}")
      }
      if (pdfBuffer.size == 0) {
        throw ConversionFailedException(jobId, exitValue, errorOutput)
      }
      pdfBuffer.toByteArray
    } finally {
      if (process.isAlive) {
        log.warn(s"[Job $jobId] Destroying wkhtmltopdf process ...")
        process.destroyForcibly()
        // the pumps see EOF once the process is gone and terminate on their own
        process.waitFor(DestroyGrace.toMillis, MILLISECONDS)
      }
    }
  }

  /** Feed the HTML into the process' STDIN, if it is not passed as a file path. */
  private def writeStdIn(jobId: String, process: Process, source: Source): Unit = {
    val stdIn = process.getOutputStream
    try {
      source match {
        case StdinSource(htmlString) =>
          stdIn.write(htmlString.getBytes(UTF_8))
          stdIn.flush()
        case _: FileSource => // the input is passed as a command line parameter
      }
    } catch {
      // a process that died immediately gives a broken pipe here; the exit code and STDERR
      // collected below describe the actual problem, so do not mask them
      case exc: IOException => log.debug(s"[Job $jobId] Could not write to wkhtmltopdf STDIN: ${exc.getMessage}")
    } finally {
      IOUtils.closeQuietly(stdIn)
    }
  }

  /** Copy a process output stream into a buffer on a daemon thread. */
  private def pump(name: String, in: InputStream, out: ByteArrayOutputStream): Thread = {
    val thread = new Thread(new Runnable {
      override def run(): Unit =
        try IOUtils.copy(in, out)
        catch {
          // expected when the process is destroyed while the pipe is being read
          case exc: IOException => log.debug(s"Stream $name closed: ${exc.getMessage}")
        } finally IOUtils.closeQuietly(in)
    }, name)
    thread.setDaemon(true)
    thread.start()
    thread
  }

  /**
    * Wait for a pump to finish reading. The process has already exited at this point, so the pipe
    * is at EOF and this returns almost immediately; a pump that is still running indicates that
    * the output cannot be trusted to be complete.
    */
  private def awaitPump(jobId: String, pump: Thread): Unit = {
    pump.join(PumpGrace.toMillis)
    if (pump.isAlive) {
      throw ProcessGeneration.ProcessErrorPdfConvert(
        s"[Job $jobId] Timed out reading ${pump.getName} after ${PumpGrace.toSeconds}s")
    }
  }

  private def validateExecutable(): Unit = {
    val executable = new File(pathToWkHtmlToPdf)
    if (!executable.canExecute) throw NoExecutableException(executable.getAbsolutePath)
  }

}

/**
  * Companion object to StepConvert.
  */
object StepConvert extends PluginLogger {

  /** Config key for the maximum runtime of a single wkhtmltopdf call. */
  final val ConversionTimeoutPath = "pdfPlugin.conversionTimeoutSeconds"

  /**
    * Fraction of the step timeout used as conversion timeout when none is configured, so that the
    * process is always destroyed before the surrounding ask gives up on the step.
    */
  final val StepTimeoutFraction = 0.9

  /** Grace period to wait for a destroyed process to actually disappear. */
  final val DestroyGrace: FiniteDuration = 5.seconds

  /** Grace period to wait for the output pumps after the process has exited. */
  final val PumpGrace: FiniteDuration = 10.seconds

  private final val QuietOption = "--quiet"
  private final val StdOutParameter = "-"
  private final val MaxLoggedOutputChars = 2000

  /**
    * Maximum runtime granted to a single wkhtmltopdf call: `pdfPlugin.conversionTimeoutSeconds` if
    * configured, otherwise a fraction of the step timeout. A configured value that is not shorter
    * than the step timeout is useless - the ask would give up first, leaving the process running -
    * and is therefore clamped.
    */
  def conversionTimeout(config: Config, stepTimeout: FiniteDuration): FiniteDuration = {
    val derived = FiniteDuration((stepTimeout.toMillis * StepTimeoutFraction).toLong, MILLISECONDS)
    Try(config.getInt(ConversionTimeoutPath)).toOption.filter(_ > 0).map(_.seconds) match {
      case Some(configured) if configured < stepTimeout => configured
      case Some(configured) =>
        log.warn(s"$ConversionTimeoutPath (${configured.toSeconds}s) is not shorter than the step " +
          s"timeout (${stepTimeout.toSeconds}s) and would never take effect - using ${derived.toSeconds}s instead.")
        derived
      case None => derived
    }
  }

  private def abbreviate(output: String): String =
    if (output.length <= MaxLoggedOutputChars) output else output.take(MaxLoggedOutputChars) + " ..."

  /** How the HTML input is handed to wkhtmltopdf. */
  private[actor] sealed trait Source {
    /** Input parameter on the command line. */
    def commandParameter: String
  }

  private[actor] case class FileSource(file: File) extends Source {
    override def commandParameter: String = file.getAbsolutePath
  }

  private[actor] case class StdinSource(htmlString: String) extends Source {
    override def commandParameter: String = StdOutParameter
  }

  case class ConversionTimeoutException(jobId: String, timeout: FiniteDuration)
    extends Exception(s"[Job $jobId] wkhtmltopdf did not finish within ${timeout.toSeconds}s and was destroyed")

  case class ConversionFailedException(jobId: String, exitValue: Int, errorOutput: String)
    extends Exception(s"[Job $jobId] wkhtmltopdf produced no output (exit code $exitValue)" +
      (if (errorOutput.isEmpty) "" else s": ${abbreviate(errorOutput)}"))

  /**
    * Result object of the step Convert.
    *
    * @param job     initial generation job
    * @param pdfData binary data of the converted PDF
    */
  case class ConvertResult(job: PdfJobAbstraction, pdfData: Array[Byte])
}
