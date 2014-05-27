package uk.gov.tna.dri.preingest.loader.unit.network

import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.SettingsImpl
import scalax.file.Path

/**
 * Created by dev on 5/27/14.
 */
object GPGCrypt extends Logging{
  def decrypt(filePathName: String, certificate: Option[Path], passphrase: String) {
    import scala.sys.process._

    val fileNameNoExtension = filePathName.substring(0, filePathName.lastIndexOf("gpg")-1)
    val decryptCmd = Seq(
      "gpg",
      "--batch",
      s"""--passphrase "$passphrase" """,
      s"--output $fileNameNoExtension",
      "--decrypt ",
      filePathName) ++ (certificate match {
      case Some(certificate) =>
        Seq(
          s"--keyfiles=${certificate.path}"
        )
      case None =>
        Nil
    })

    //todo laura unify
    val unzipCmd = Seq(
      "unzip",
      s"$fileNameNoExtension.zip")


    println("******************About to execute "+ decryptCmd )
    val gpgCode = decryptCmd  !

    if(gpgCode != 0) {
      error(s"Error code '$gpgCode' when executing: $decryptCmd")
    }

    println("******************About to execute "+ unzipCmd )
    val resultCode = unzipCmd.!
    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $unzipCmd")
    }
  }
}
