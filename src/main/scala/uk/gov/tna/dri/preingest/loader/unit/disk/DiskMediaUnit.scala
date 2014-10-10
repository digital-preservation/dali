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
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._
import java.io.{InputStream, OutputStream, IOException}
import akka.actor.{Props, ActorRef}
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
import uk.gov.tna.dri.preingest.loader.unit.PartitionDetailsForEncryptionMethodChange
import uk.gov.tna.dri.preingest.loader.unit.SendPartitionDetails
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.DiskProperties
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.PartitionProperties

trait PartitionUnit extends MediaUnit {
  val partition: PartitionProperties
  val disk: DiskProperties

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

  def getProperties():(PartitionProperties, DiskProperties) = {
    (this.partition, this.disk)
  }

}

trait GenericEncryptedPartitionUnit extends PartitionUnit with EncryptedDRIUnit

case class NonEncryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends PartitionUnit with NonEncryptedDRIUnit

case class EncryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends GenericEncryptedPartitionUnit

case class TrueCryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends GenericEncryptedPartitionUnit

case class LUKSEncryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends GenericEncryptedPartitionUnit

