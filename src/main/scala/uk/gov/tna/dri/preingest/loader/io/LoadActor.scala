/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.io

import akka.actor.Actor
import grizzled.slf4j.Logging
import uk.gov.nationalarchives.dri.preingest.loader.unit.LoadUnit
import uk.gov.nationalarchives.dri.preingest.loader.store.DataStore
import scalax.file.{PathSet, Path}
import scalax.file.PathMatcher.IsFile
import uk.gov.nationalarchives.dri.preingest.loader.unit.disk.{TrueCrypt, TrueCryptedPartition}


object LoadStatus extends Enumeration {
  type LoadStatus = Value

  val Starting = Value("Starting")
  val InProgress = Value("In Progress")
  val Complete = Value("Complete")
}

/**
 * TODO This is probably no longer used - it may be needed for
 * reporting the status of loading a unit during copy
 * operation, need to check this - Adam.
 */
case class UnitLoadStatus(src: String, complete: Int, status: LoadStatus.LoadStatus)

//class LoadActor extends Actor with Logging {

  import LoadStatus._

//  def receive = {
//
//    case LoadUnit(username, loadingUnit, certificate, passphrase) =>
//      DataStore.withTemporaryFile(certificate) {
//        cert =>
//          val mountPoint = TrueCryptedPartition.tempMountPoint(username, loadingUnit.src)
//        //TODO This only copes with TrueCrypt at the moment, should be able to do any type of copy, but for the moment we need the passphrase.get to cope with this
//          TrueCrypt.withVolume(loadingUnit.src, cert, passphrase.get, mountPoint) {
//            val files = mountPoint ** IsFile filterNot { f => DataStore.isWindowsJunkDir(f.parent.get.name) }
//            val total = totalSize(files)
//            sender ! UnitLoadStatus(loadingUnit.src, 0, Starting)
//
//            var completed: Long = 0
//            for(file <- files) {
//              val label = loadingUnit.label.split(' ')(0) //TODO temp remove when label does not include the filesystem info
//              val destination = DESTINATION / label / Path.fromString(file.path.replace(mountPoint.path + "/", ""))
//              loadFile(file, destination)
//              completed += file.size.get
//
//              val percentageDone = ((completed.toDouble / total) * 100).toInt
//
//              sender ! UnitLoadStatus(loadingUnit.src, percentageDone, InProgress)
//            }
//
//            sender ! UnitLoadStatus(loadingUnit.src, 100, Complete)
//          }
//      }
//  }

//  def loadFile(file: Path, dest: Path) = file.copyTo(dest, createParents = true, copyAttributes = true)

//  def totalSize(paths: PathSet[Path]) = paths.toList.map(_.size).map(_.getOrElse(0l)).reduceLeft(_ + _)
//}
