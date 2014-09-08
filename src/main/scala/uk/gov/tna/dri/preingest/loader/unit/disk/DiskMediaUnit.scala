package uk.gov.tna.dri.preingest.loader.unit.disk

import uk.gov.tna.dri.preingest.loader.unit._
import uk.gov.tna.dri.preingest.loader.store.DataStore
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.DiskProperties
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.PartitionProperties
import scalax.file.{PathMatcherFactory, LinkOption, PathSet, Path}
import scalax.file.PathMatcher.IsFile
import uk.gov.tna.dri.preingest.loader.certificate.CertificateDetail
import uk.gov.tna.dri.preingest.loader.{SettingsImpl, Crypto}
import uk.gov.tna.dri.preingest.loader.Crypto.DigestAlgorithm
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.{OrphanedFileName, PartName}
import java.io.{InputStream, OutputStream, IOException}
import akka.actor.ActorRef
import scala.util.control.Breaks._
import grizzled.slf4j.Logger
import uk.gov.tna.dri.preingest.loader.unit.common.MediaUnitActor
import uk.gov.tna.dri.preingest.loader.unit.TargetedPart
import scala.Some
import uk.gov.tna.dri.preingest.loader.unit.UnitError
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.DiskProperties
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.PartitionProperties
import scalax.file.Path.AccessModes.AccessMode
import scalax.io.{Seekable, ResourceContext, SeekableByteChannel, OpenOption}
import scalax.io.managed.{InputStreamResource, SeekableByteChannelResource, OutputStreamResource}
import java.net.URI

trait PartitionUnit extends MediaUnit {
  protected val partition: PartitionProperties
  protected val disk: DiskProperties

  def uid = Crypto.hexUnsafe(Crypto.digest(partition.nativePath, None, DigestAlgorithm.MD5))
  def unitType = "Partition"
  def interface = partition.interface.toUpperCase
  def src = partition.deviceFile
  def label = partition.partitionLabel.getOrElse(if(encrypted) {
    "<<ENCRYPTED>>"
  }else{
    "<<UNKNOWN>>"
  })
  def size = partition.size
  def timestamp = partition.timestamp * 1000 //covert to milliseconds

  override def toJson() = {
    import org.json4s.JsonDSL._

    super.toJson() ~
    ("filesystem" -> PartitionTypes.TYPES(partition.partitionType)) ~
    ("disk" ->
      ("vendor" -> disk.vendor) ~
      ("model" -> disk.model) ~
      ("serial" -> disk.serial)
    )
  }

}

trait EncryptedPartitionUnit extends PartitionUnit with EncryptedDRIUnit
case class TrueCryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends EncryptedPartitionUnit


class TrueCryptedPartitionUnitActor(var unit: TrueCryptedPartitionUnit) extends MediaUnitActor[TrueCryptedPartitionUnit] with EncryptedDRIUnitActor[TrueCryptedPartitionUnit] { //TODO consider subclassing PhysicalUnit

  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], unitManager: Option[ActorRef]): Unit = copyData(username, parts, None, passphrase, unitManager)

  def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String], unitManager: Option[ActorRef]): Unit = {
    DataStore.withTemporaryFile(Option(certificate)) {
      cert =>
        copyData(username, parts, cert, passphrase, unitManager)
    }
  }

  def updateDecryptDetail(username: String, passphrase: String) = ??? //updateDecryptDetail(username, , None, passphrase)

  def updateDecryptDetail(username: String, listener: ActorRef, certificate: CertificateDetail, passphrase: String) {
    val retCode = DataStore.withTemporaryFile(Option(certificate)) {
      cert =>
        updateDecryptDetail(username, listener, cert, passphrase)
    }
    if (!retCode)
      listener ! UnitError(unit, "Unable to decrypt data for unit ")
  }

  private def updateDecryptDetail(username: String, listener: ActorRef, certificate: Option[Path], passphrase: String) : Boolean = {
    TrueCryptedPartition.getVolumeLabel(settings, unit.src, certificate, passphrase).map {
      volumeLabel =>

        //extract parts and orphaned files
        tempMountPoint(username, unit.src) match {
          case Left(ioe) =>
            listener ! UnitError(unit, "Unable to decrypt data for unit: " + unit.uid)
            error(s"Unable to update decrypted detail for unit: ${unit.uid}", ioe)

            false
          case Right(tempMountPoint) =>
            val (dirs, files) = TrueCryptedPartition.listTopLevel(settings, unit.src, tempMountPoint, certificate, passphrase)(_.partition(_.isDirectory))
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
        TrueCrypt.withVolume(settings, unit.partition.deviceFile, certificate, passphrase.get, mountPoint) {
            copyFiles( parts, mountPoint,  unitManager)
        }
      }
    }
  }

case class NonEncryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends PartitionUnit with NonEncryptedDRIUnit


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

  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], unitManager: Option[ActorRef]) {
    tempMountPoint(username, unit.partition.deviceFile) match {
      case Left(ioe) =>
        error(s"Unable to copy data for unit: ${unit.uid}", ioe)
        unitManager match {
          case Some(sender) =>  sender ! UnitError(unit, "Unable to copy data for unit:" + ioe.getMessage)
          case None =>
        }
      case Right(mountPoint) =>
        copyFiles( parts, mountPoint,  unitManager)
    }
  }
}

