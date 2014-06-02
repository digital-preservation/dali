package uk.gov.tna.dri.preingest.loader.unit.network

import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.SettingsImpl
import scalax.file.Path


object GPGCrypt extends Logging{
  def decryptAndUnzip(filePathName: String, certificate: Option[Path], passphrase: String) {
    import scala.sys.process._

    val fileFolder = filePathName.substring(0, filePathName.lastIndexOf("/"))
    val fileNameNoGPGExtension = filePathName.substring(0, filePathName.lastIndexOf("gpg")-1)

    val decryptCmd = Seq(
      "gpg",
      "--batch",
      s"--output=$fileNameNoGPGExtension",
      s"--passphrase=$passphrase",
      "--decrypt",
      filePathName) ++ (certificate match {
      case Some(certificate) =>
        //todo laura certificate
//        Seq(
//          s"--keyfiles=${certificate.path}"
//        )
        Nil
      case None =>
        Nil
      case _ =>
        Nil
    })

    val unzipCmd = Seq(
      "unzip",
      "-u",
      s"-d$fileFolder",
      s"$fileNameNoGPGExtension"
      )


    val gpgCode = decryptCmd.!

    if(gpgCode != 0) {
      error(s"Error code '$gpgCode' when executing: $decryptCmd")
    }

    val unzipCode = unzipCmd.!

    if(unzipCode != 0) {
      error(s"Error code '$unzipCode' when executing: $unzipCmd")
    }

    val cleanupCmd = Seq(
      "rm",
      "-f",
      filePathName,
      fileNameNoGPGExtension
    )

    val rmCode = cleanupCmd.!

    if(unzipCode != 0) {
      error(s"Error code '$rmCode' when executing: $cleanupCmd")
    }


  }
}