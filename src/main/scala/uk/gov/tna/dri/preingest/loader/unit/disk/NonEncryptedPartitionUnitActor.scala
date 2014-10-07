package uk.gov.tna.dri.preingest.loader.unit.disk

import uk.gov.tna.dri.preingest.loader.unit._
import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.SettingsImpl
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.{OrphanedFileName, PartName}
import akka.actor.{Props, ActorRef}
import scala.util.control.Breaks._
import grizzled.slf4j.Logger
import uk.gov.tna.dri.preingest.loader.unit.common.MediaUnitActor
import uk.gov.tna.dri.preingest.loader.unit.TargetedPart
import scala.Some
import uk.gov.tna.dri.preingest.loader.unit.UnitError
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.DiskProperties
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.PartitionProperties


class NonEncryptedPartitionUnitActor(var unit: NonEncryptedPartitionUnit) extends MediaUnitActor[NonEncryptedPartitionUnit] {

  // get Parts on instantiation
  unit.partition.mounted match {
    case None =>
      error(s"Unable to update parts for unit: ${unit.uid}")
    case Some(pathStrs) =>
      val (dirs, files) = listTopLevel(settings, unit.src, Path.fromString(pathStrs.head))(_.partition(_.isDirectory))
      //update the unit
      this.unit = unit.copy(partition = unit.partition.copy(partitionLabel = Option(unit.label)), parts = Option(dirs.map(_.name)), orphanedFiles = Option(files.map(_.name)))
  }

  private def listTopLevel[T](settings: SettingsImpl, volume: String, mount: Path)(f: Seq[Path] => T): T = {
    import uk.gov.tna.dri.preingest.loader.unit.common.unit.isJunkFile
    val files = mount * ((p: Path) => !isJunkFile(settings, p.name))
    f(files.toSet.toSeq)
  }

  def fixityCheck(username: String, part: TargetedPart, passphrase: Option[String], unitManager: Option[ActorRef]) {

    // we use the mountpoint created by the OS, not needing a new local one
    unit.partition.mounted match {
      case Some(mountPointList) => {
        val mountPoint = scalax.file.Path.fromString(mountPointList.head)
        fixityCheck( part.part, mountPoint, unitManager)
      }
      case None =>
        unitManager match {
          case Some(sender) =>  sender ! UnitError(unit, "Unable to find mountpoint for unit: ${unit.uid}")
          case None =>
        }
    }
  }

  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], unitManager: Option[ActorRef]) {

    // we use the mountpoint created by the OS, not needing a new local one
    unit.partition.mounted match {
      case Some(mountPointList) => {
        val mountPoint = scalax.file.Path.fromString(mountPointList.head)
        copyFiles( parts, mountPoint, unitManager)
      }
      case None =>
        unitManager match {
          case Some(sender) =>  sender ! UnitError(unit, "Unable to find mountpoint for unit: ${unit.uid}")
          case None =>
        }
    }
  }
}
object NonEncryptedPartitionUnitActor {
  def props(unit: NonEncryptedPartitionUnit): Props = Props(new NonEncryptedPartitionUnitActor(unit))
}
