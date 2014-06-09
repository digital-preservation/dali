package uk.gov.tna.dri.preingest.loader.unit.network

import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.SettingsImpl
import scalax.file.Path
import scala.sys.process.Process


object GPGCrypt extends Logging{
  def decryptAndUnzip(filePathName: String, certificate: Option[Path], passphrase: String): Int = {
    import scala.sys.process._

    val fileFolder = filePathName.substring(0, filePathName.lastIndexOf("/"))
    val fileNameNoGPGExtension = filePathName.substring(0, filePathName.lastIndexOf("gpg")-1)

    certificate match {
      case Some(certificate) => {
        //gpg --allow-secret-key-import --import private.key

        val importCertCmd = Seq(
          "gpg",
          "--allow-secret-key-import",
          "--import",
          certificate.path
        )
        executeCommand(importCertCmd)
      }
      case _ =>
    }



    val decryptCmd = Seq(
      "gpg",
      "--batch",
      s"--output=$fileNameNoGPGExtension",
      s"--passphrase=$passphrase",
      "--decrypt",
      filePathName)
    val gpgCode =  executeCommand(decryptCmd)

    val unzipCmd = Seq(
      "unzip",
      "-u",
      s"-d$fileFolder",
      s"$fileNameNoGPGExtension"
    )

    // val t = unzipCmd.!
    val unzipCode = executeCommand(unzipCmd)


    val cleanupCmd = Seq(
      "rm",
      "-f",
      filePathName,
      fileNameNoGPGExtension
    )
    val rmCode = executeCommand(cleanupCmd)

    return gpgCode
  }

  private def executeCommand(command: Seq[String]): Int = {
    import scala.sys.process._
    val returnCode  = command.!
    if (returnCode != 0) {
      error(s"Error code '$returnCode' when executing: $command")
    }
    returnCode
  }
}