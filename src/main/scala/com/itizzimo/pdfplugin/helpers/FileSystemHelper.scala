package com.itizzimo.pdfplugin.helpers

import java.io.{File, IOException}

import com.typesafe.config.Config

class FileSystemHelper(config: Config) {

  /**
    * Resolve temp directory for job.
    *
    * @param jobId target jobId
    * @return temp dir as File (not existing, yet)
    */
  def resolveTempDir(jobId: String): File = {
    val tempBaseDir = new File(config getString "pdfPlugin.tempDir")
    new File(tempBaseDir, jobId)
  }

  /**
    * Create temp directory.
    */
  def mkTempDir(jobId: String): File = {
    val tempDir = resolveTempDir(jobId)
    if (!tempDir.mkdirs() && !tempDir.isDirectory) {
      throw new IOException("Could not create temp dir")
    }
    tempDir
  }
}
