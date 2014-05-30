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
    } catch {
      case ioe: IOException =>
        Left(ioe)
    }
  }

  protected def totalSize(paths: PathSet[Path]) = paths.toList.map(_.size).map(_.getOrElse(0l)).reduceLeft(_ + _)
}