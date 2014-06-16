package uk.gov.tna.dri.preingest.loader.unit.common

import uk.gov.tna.dri.preingest.loader.unit._
import uk.gov.tna.dri.preingest.loader.store.DataStore
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.DiskProperties
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.PartitionProperties
import scalax.file.{PathSet, Path}
import scalax.file.PathMatcher.IsFile
import uk.gov.tna.dri.preingest.loader.certificate.CertificateDetail
import uk.gov.tna.dri.preingest.loader.Crypto
import uk.gov.tna.dri.preingest.loader.Crypto.DigestAlgorithm
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.{OrphanedFileName, PartName}
import java.io.IOException
import akka.actor.ActorRef
import scala.util.control.Breaks._
import grizzled.slf4j.Logger

trait PhysicalMediaUnitActor[T <: PhysicalMediaUnit] extends DRIUnitActor[T] {

  protected def tempMountPoint(username: String, volume: String) : Either[IOException , Path] = {
    DataStore.userStore(settings, username) match {
      case l@ Left(ioe) =>
        l
      case Right(userStore) =>
        val mountPoint = userStore / s"${volume.split('/').last}"
        if (!mountPoint.exists) {
          try {
            Right(mountPoint.createDirectory(createParents = true, failIfExists = true))
          } catch {
            case ioe: IOException =>
              Left(ioe)
          }
        } else {
          Right(mountPoint)
        }
    }
  }

  protected def copyFile(file: Path, dest: Path) : Either[IOException, Path] = {
    try{
      Right(file.copyTo(dest, createParents = true, copyAttributes = true))
      //laura todo - remove original file + loading

    } catch {
      case ioe: IOException =>
        Left(ioe)
    }
  }

  protected def totalSize(paths: PathSet[Path]) = paths.toList.map(_.size).map(_.getOrElse(0l)).reduceLeft(_ + _)

  protected def copyFiles(parts: Seq[TargetedPart], mountPoint: Path, unitManager: Option[ActorRef]) {
    val all = mountPoint ** IsFile filter { f => true}
    val files = mountPoint ** IsFile filter { f => parts.exists(p => p.part.series == DataStore.getTopParent(f, mountPoint))}

    if (files.isEmpty) {
      unitManager match {
        case Some(sender) =>
          sender ! UnitError(unit, "No files found ")
          break // break on first error
        case None => break // break on error
      }
    }
    else {
      val total = totalSize(files)
      unitManager match {
        case Some(sender) => sender ! UnitProgress(unit, parts, 0)
        case None =>
      }
      var completed: Long = 0

      breakable {

        val fileIndex = files.toIndexedSeq

        for (file <- files) {

          //match destination
          val root  = DataStore.getTopParent(file, mountPoint)
          val pInd = parts.indexWhere{p => p.part.series == root }
          val destLocation = Destination.invert(parts(pInd).destination)

          //add the label - e.g archive name
          val labelDestination = unit.label.substring(unit.label.lastIndexOf("/") + 1)

          val destination =  settings.Unit.destination / Path.fromString(destLocation) / Path.fromString(labelDestination) / Path.fromString(file.relativize(mountPoint).path)

          copyFile(file, destination) match {
            case Left(ioe) =>
              error(s"Unable to copy data for unit: ${unit.uid}", ioe)
              unitManager match {
                case Some(sender) =>
                  sender ! UnitError(unit, "Unable to copy data for unit: " + ioe.getMessage)
                  break // break on first error
                case None => break // break on error
              }
            case Right(path) =>
              completed += file.size.getOrElse(0L)
              val percentageDone = Math.ceil((completed.toDouble / total) * 100).toInt
              trace(s"[{$percentageDone}%] Copied file: ${file.path}")
              unitManager match {
                case Some(sender) =>
                  if((percentageDone >= 100) && (fileIndex.indexOf(file) == (files.size -1))) {
                    info(s"Finished Copying Unit: ${parts.head.part.unitId}")
                    sender ! UnitProgress(unit, parts, 100) // make sure we only send 100% once and not once for each file!
                    break // we have reached 100% copied so there is nothing more to do here
                  } else {
                    sender ! UnitProgress(unit, parts, percentageDone)
                  }
                case None =>
              }
          }
        }
      }
    }
  }
}