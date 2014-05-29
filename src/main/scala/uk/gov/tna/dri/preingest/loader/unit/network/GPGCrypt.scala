package uk.gov.tna.dri.preingest.loader.unit.network

import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.SettingsImpl
import scalax.file.Path


object GPGCrypt extends Logging{
  def decryptAndUnzip(filePathName: String, certificate: Option[Path], passphrase: String) {
    import scala.sys.process._

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

    //todo laura unify
    val unzipCmd = Seq(
      "unzip",
      "-u",
      s"$fileNameNoGPGExtension")


    println("******************About to execute "+ decryptCmd )
    val gpgCode = decryptCmd #&& unzipCmd !

    if(gpgCode != 0) {
      error(s"Error code '$gpgCode' when executing: $decryptCmd")
    }
  }
}