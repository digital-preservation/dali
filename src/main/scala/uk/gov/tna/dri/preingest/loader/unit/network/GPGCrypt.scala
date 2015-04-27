/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.tna.dri.preingest.loader.unit.network

import grizzled.slf4j.Logging
import scalax.file.Path


object GPGCrypt extends Logging{
  def decryptAndUnzip(filePathName: String, certificate: Option[Path], passphrase: String): Int = {

    val fileFolder = filePathName.substring(0, filePathName.lastIndexOf("/"))
    //  val fileNameNoGPGExtension = filePathName.substring(0, filePathName.lastIndexOf("gpg")-1)
    val tempZipFileName = s"$filePathName.zip"

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
      s"--output=$tempZipFileName",
      s"--passphrase=$passphrase",
      "--decrypt",
      filePathName)

    val gpgCode =  executeCommand(decryptCmd)

    val unzipCmd = Seq(
      "unzip",
      "-u",
      s"-d$fileFolder",
      s"$tempZipFileName"
    )

    val unzipCode = executeCommand(unzipCmd)

    val cleanupCmd = Seq(
      "rm",
      "-f",
      filePathName,
      tempZipFileName
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