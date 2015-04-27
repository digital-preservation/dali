/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.tna.dri.preingest.loader.unit.network

import uk.gov.tna.dri.preingest.loader.Settings
import fr.janalyse.ssh.SSHOptions

//global variable
object GlobalUtil {

  //protected val settings = Settings(context.system)

  var processing = false

  def initProcessing(opts:SSHOptions, loadFile: String){
    processing = true
    RemoteStore.createFile(opts, loadFile)
  }

  def cleanupProcessing(opts:SSHOptions, cleanupFile: String){
    processing = false
    RemoteStore.deleteFile(opts, cleanupFile)
  }
}
