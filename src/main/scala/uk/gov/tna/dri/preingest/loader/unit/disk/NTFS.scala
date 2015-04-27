/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import scala.collection.mutable
import uk.gov.nationalarchives.dri.preingest.loader.SettingsImpl

object NTFS extends Logging {

  def getLabel(settings: SettingsImpl, volume: String) : Option[String] = {
    import scala.sys.process._

    val labelCmd = Seq("sudo", settings.NtfsProgs.labelBin.path, volume)

    val labelLogger = new ProcessLogger {
      val stdOut = new mutable.ListBuffer[String]
      def out(s: => String) = stdOut += s
      def err(s: => String) = error(s)
      def buffer[T](f: => T) = f
    }

    val resultCode = labelCmd ! labelLogger
    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $labelCmd")
    }

    val label = labelLogger.stdOut.reduceLeft(_.trim + _.trim)
    if(label.isEmpty) {
      None
    } else {
      Option(label)
    }
  }
}
