/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.store

import scalax.file.Path
import uk.gov.nationalarchives.dri.preingest.loader.{SettingsImpl, Crypto}
import java.io.IOException
import uk.gov.nationalarchives.dri.preingest.loader.unit.TargetedPart

object DataStore {

  /**
   * Path to a KeyStore for a user
   *
   * Generates a KeyStore path based on the username
   */
  def userStore(settings: SettingsImpl, username: String) : Either[IOException, Path] = {
    val dUsername = Crypto.base64Unsafe(Crypto.digest(username, None, settings.DataStore.digestAlgorithm))
    val store = settings.DataStore.userData / dUsername.filter(_ != '/')

    if(!store.exists) {
      try {
        Right(store.createDirectory(createParents = true, failIfExists = false))
      } catch {
        case ioe: IOException =>
          Left(ioe)
      }
    } else {
      Right(store)
    }
  }

  /**
   * Writes data to a temporary file
   * and performs a function
   * on the document.
   *
   * The temporary file
   * will be deleted after `f`
   * is finished
   *
   * @param fileDetail The name and content for the file
   * @param f The function to perform on the file
   *
   * @return The result of `f`
   */
  def withTemporaryFile[T](fileDetail: Option[(String, Array[Byte])])(f: Option[Path] => T) : T = fileDetail match {
    case Some((name, data)) =>
      val tmpFile = Path.createTempFile(deleteOnExit = true)
      try {
        tmpFile.write(data)
        f(Option(tmpFile))
      } finally {
        tmpFile.delete(force = true)
      }
    case None =>
      f(None)
  }

  /**
   *
   * Gets the top folder of a file, omitting the mount path
   * @param file the complete path of the file, e.g. /tmp/Recycle Bin/file
   * @param mount   the mount path e.g. /tmp
   * @return the top folder e.g Recycle Bin
   *
   */
  def getTopParent(file:Path, mount:Path) : String = {
    val relFile = file relativize mount
    val root = relFile.segments.head
    return root
  }

}
