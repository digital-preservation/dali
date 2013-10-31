package uk.gov.tna.dri.preingest.loader.unit.disk

import uk.gov.tna.dri.preingest.loader.unit._
import uk.gov.tna.dri.preingest.loader.store.DataStore
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.DiskProperties
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.PartitionProperties
import scalax.file.{PathSet, Path}
import scalax.file.PathMatcher.IsFile
import uk.gov.tna.dri.preingest.loader.certificate.CertificateDetail
import uk.gov.tna.dri.preingest.loader.Crypto
import uk.gov.tna.dri.preingest.loader.Crypto.DigestAlgorithm

trait PhysicalMediaUnitActor[T <: PhysicalMediaUnit] extends DRIUnitActor[T] {

  protected val DESTINATION = Path.fromString("/unsafe_in")  //TODO make configurable

  val partition: PartitionProperties
  val disk: DiskProperties

  def uid = partition.deviceFile

  protected def tempMountPoint(username: String, volume: String) : Path = {
    val mountPoint = DataStore.userStore(username) / s"${volume.split('/').last}"
    if(!mountPoint.exists) {
      mountPoint.createDirectory()
    }
    mountPoint
  }

  protected def copyFile(file: Path, dest: Path) = file.copyTo(dest, createParents = true, copyAttributes = true)

  protected def totalSize(paths: PathSet[Path]) = paths.toList.map(_.size).map(_.getOrElse(0l)).reduceLeft(_ + _)
}

trait PartitionUnit extends PhysicalMediaUnit {
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
      ("model" -> disk.vendor) ~
      ("serial" -> disk.serial)
    )
  }
}

trait EncryptedPartitionUnit extends PartitionUnit with EncryptedDRIUnit


case class TrueCryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties) extends EncryptedPartitionUnit

class TrueCryptedPartitionUnitActor(val partition: PartitionProperties, val disk: DiskProperties) extends PhysicalMediaUnitActor[TrueCryptedPartitionUnit] { //TODO consider subclassing PhysicalUnit

  def unit = TrueCryptedPartitionUnit(partition, disk)

  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String]) = copyData(username, parts, None, passphrase)


  def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String]) {
    DataStore.withTemporaryFile(Option(certificate)) {
      cert =>
        copyData(username, parts, cert, passphrase)
    }
  }

  private def copyData(username: String, parts: Seq[TargetedPart], certificate: Option[Path], passphrase: Option[String]) {
    val mountPoint = tempMountPoint(username, partition.deviceFile)
    TrueCrypt.withVolume(partition.deviceFile, certificate, passphrase.get, mountPoint) {
      val files = mountPoint ** IsFile filterNot { f => DataStore.isWindowsJunkDir(f.parent.get.name) }
      val total = totalSize(files)
      sender ! UnitStatus(unit, Option(UnitAction(0))) //TODO inject sender?

      var completed: Long = 0
      for(file <- files) {
        val label = unit.label.split(' ')(0) //TODO temp remove when label does not include the filesystem info
        val destination = DESTINATION / label / Path.fromString(file.path.replace(mountPoint.path + "/", ""))
        copyFile(file, destination)
        completed += file.size.get

        val percentageDone = ((completed.toDouble / total) * 100).toInt

        sender ! UnitStatus(unit, Option(UnitAction(percentageDone))) //TODO inject sender?
      }

      sender ! UnitStatus(unit, Option(UnitAction(100))) //TODO inject sender?
    }
  }
}

case class UnencryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties) extends PartitionUnit {
  val encrypted = false
}

//TODO not yet implemented
class UnencryptedPartitionUnitActor(val partition: PartitionProperties, val disk: DiskProperties) extends PhysicalMediaUnitActor[UnencryptedPartitionUnit] { //TODO consider subclassing PhysicalUnit

  def unit = UnencryptedPartitionUnit(partition, disk)

  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String]) = ???

  def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String]) = ???
}

//def

//  val unit = {
//   PhysicalUnit(uid, partition.interface.toUpperCase, partition.deviceFile, partition.getLabel(), partition.size, partition.timestamp * 1000)
//  } //*1000 to get to milliseconds

  //private def pendingUnit(sp: StoreProperties) = PendingUnit(sp.interface.toUpperCase, sp.deviceFile, sp.getLabel(), Option(sp.size), Option(sp.timestamp * 1000)) //*1000 to get to milliseconds

  //def receive = {

    //TODO subclass of DRI which represents encrypted unit

    //case du: DecryptUnit =>

    //TODO this should not be in here!
//    case du: DecryptUnit =>
//      getDecryptedUnitDetails(du).map {
//      case upPendingUnit =>
//        //update state
//        val existingPendingUnit = this.known.find(_.src == du.pendingUnit.src).get
//        val updatedPendingUnit = upPendingUnit.copy(timestamp = existingPendingUnit.timestamp, size = existingPendingUnit.size) //preserve timestamp and size
//        this.known = this.known.updated(this.known.indexWhere(_.src == updatedPendingUnit.src), updatedPendingUnit)
//
//        //update sender
//        sender ! DeRegister(existingPendingUnit)
//        sender ! Register(updatedPendingUnit)
//    }
//  }
//
//  def getDecryptedUnitDetails(du: DecryptUnit) : Option[DRIUnit] = {
//    DataStore.withTemporaryFile(du.certificate) {
//      tmpCert =>
//        TrueCryptedPartition.getVolumeLabel(du.pendingUnit.src, tmpCert, du.passphrase.get).map {
//          volumeLabel =>
//            val updatedPendingUnitLabel = du.pendingUnit.label.replace(DBusUDisks.UNKNOWN_LABEL, volumeLabel)
//            val prts = TrueCryptedPartition.listTopLevelDirs(du.username)(du.pendingUnit.src, tmpCert, du.passphrase.get).map(Part(volumeLabel, _))
//            du.pendingUnit.copy(label = updatedPendingUnitLabel, parts = Some(prts))
//        }
//    }
//  }
  //}

//}
