/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.tna.dri.preingest.loader.unit.disk

import uk.gov.tna.dri.preingest.loader.unit._
import uk.gov.tna.dri.preingest.loader.store.DataStore
import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.certificate.CertificateDetail
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.{OrphanedFileName, PartName}
import akka.actor.{Props, ActorRef}
import scala.util.control.Breaks._
import grizzled.slf4j.Logger
import uk.gov.tna.dri.preingest.loader.unit.common.MediaUnitActor
import uk.gov.tna.dri.preingest.loader.unit.TargetedPart
import scala.Some
import uk.gov.tna.dri.preingest.loader.unit.UnitError


class LUKSEncryptedPartitionUnitActor(var unit: LUKSEncryptedPartitionUnit) extends MediaUnitActor[LUKSEncryptedPartitionUnit] with EncryptedDRIUnitActor[LUKSEncryptedPartitionUnit] {

  //TODO stub
  def fixityCheck(username: String, part: TargetedPart, passphrase: Option[String], unitManager: Option[ActorRef]) {

  }

  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], unitManager: Option[ActorRef]): Unit = copyData(username, parts, None, passphrase, unitManager)

  def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String], unitManager: Option[ActorRef]): Unit = {
    DataStore.withTemporaryFile(Option(certificate)) {
      cert =>
        copyData(username, parts, cert, passphrase, unitManager)
    }
  }

  def updateDecryptDetail(username: String, passphrase: String) = ??? // updateDecryptDetail(username, , None, passphrase)

  def updateDecryptDetail(username: String, listener: ActorRef, certificate: CertificateDetail, passphrase: String) {
    val retCode = DataStore.withTemporaryFile(Option(certificate)) {
      cert =>
        updateDecryptDetail(username, listener, cert, passphrase)
    }
    if (!retCode)
      listener ! UnitError(unit, "Unable to decrypt data for unit ")
  }

  private def updateDecryptDetail(username: String, listener: ActorRef, certificate: Option[Path], passphrase: String) : Boolean = {
    LUKSEncryptedPartition.getVolumeLabel(settings, unit.src, certificate, passphrase).map {
      volumeLabel =>

        //extract parts and orphaned files
        tempMountPoint(username, unit.src) match {
          case Left(ioe) =>
            listener ! UnitError(unit, "Unable to decrypt data for unit: " + unit.uid)
            error(s"Unable to update decrypted detail for unit: ${unit.uid}", ioe)

            false
          case Right(tempMountPoint) =>
            val (dirs, files) = LUKSEncryptedPartition.listTopLevel(settings, unit.src, tempMountPoint, certificate, passphrase)(_.partition(_.isDirectory))
            //update the unit
            this.unit = this.unit.copy(partition = this.unit.partition.copy(partitionLabel = Option(volumeLabel)), parts = Option(dirs.map(_.name)), orphanedFiles = Option(files.map(_.name)))
            true
        }
    }.getOrElse(false)
  }

  private def copyData(username: String, parts: Seq[TargetedPart], certificate: Option[Path], passphrase: Option[String], unitManager: Option[ActorRef]) {
    tempMountPoint(username, unit.partition.deviceFile) match {
      case Left(ioe) =>
        error(s"Unable to copy data for unit: ${unit.uid}", ioe)
        unitManager match {
          case Some(sender) =>  sender ! UnitError(unit, "Unable to copy data for unit:" + ioe.getMessage)
          case None =>
        }

      case Right(mountPoint) =>
        LUKS.withVolume(settings, unit.partition.deviceFile, certificate, passphrase.get, mountPoint) {
            copyFiles( parts, mountPoint,  unitManager)
        }
      }
    }
  }

// See http://doc.akka.io/docs/akka/snapshot/scala/actors.html : Recommended Practices
object LUKSEncryptedPartitionUnitActor {
  def props(unit: LUKSEncryptedPartitionUnit): Props = Props(new LUKSEncryptedPartitionUnitActor(unit))
}
