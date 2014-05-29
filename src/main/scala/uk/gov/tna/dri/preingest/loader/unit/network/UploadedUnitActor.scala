package uk.gov.tna.dri.preingest.loader.unit

import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._
import akka.actor.ActorRef
import uk.gov.tna.dri.preingest.loader.PreIngestLoaderActor
import uk.gov.tna.dri.preingest.loader.util.RemotePath
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DiskProperties, PartitionProperties}
import uk.gov.tna.dri.preingest.loader.unit.disk.TrueCryptedPartition
import uk.gov.tna.dri.preingest.loader.certificate._
import uk.gov.tna.dri.preingest.loader.Settings
import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.unit.network.{GPGCrypt, RemoteStore}
import uk.gov.tna.dri.preingest.loader.store.DataStore

case class UploadedUnit(uid: UnitUID, interface: Interface, src: Source, label: Label, size: Bytes, timestamp: Milliseconds, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends ElectronicAssemblyUnit with EncryptedDRIUnit {
  def unitType = "Uploaded"
  def humanId = label
}

//case class GPGEnryptedPartitionUnit(partition: PartitionProperties, disk: DiskProperties, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends EncryptedPartitionUnit

class UploadedUnitActor(val uid: DRIUnit.UnitUID, val unitPath: RemotePath) extends DRIUnitActor[UploadedUnit] with EncryptedDRIUnitActor[UploadedUnit] with Logging{

  //todo laura remove duplicate
  private val opts  = RemoteStore.createOpt(settings.Unit.sftpServer, settings.Unit.username, settings.Unit.certificateFile, settings.Unit.timeout)

  var unit = UploadedUnit(uid, settings.Unit.uploadedInterface, settings.Unit.uploadedSource.path, unitPath.name, unitPath.size, unitPath.lastModified)

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests
  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], clientSender: Option[ActorRef]) {
    println("ld it should first copy the data in order to decrypt it ")
  }

  def copyData(username: String,parts: Seq[uk.gov.tna.dri.preingest.loader.unit.TargetedPart],certificate: (uk.gov.tna.dri.preingest.loader.certificate.CertificateName,
    uk.gov.tna.dri.preingest.loader.certificate.CertificateData),passphrase: Option[String],unitManager: Option[akka.actor.ActorRef]): Unit = ???

  def updateDecryptDetail(username: String, certificate: CertificateDetail, passphrase: String) {

    //create load file

    //copy the file
    val remoteFileName = unitPath.name

    val localFileName = settings.Unit.tempDestination + "/" + remoteFileName.substring(remoteFileName.lastIndexOf("/")+1)
    info("ld receiving file " + remoteFileName + " and copying to " + localFileName)
    //todo laura - check for exceptions
    RemoteStore.receiveFile(opts, remoteFileName, localFileName)

     //decrypt the file
    //def decrypt(filePathName: String, certificate: Option[Path], passphrase: String, extraCmdOptions: Seq[String]) {
    DataStore.withTemporaryFile(Option(certificate)) {
      cert =>
        GPGCrypt.decryptAndUnzip(localFileName, cert, passphrase )
    }

    val tempLocation = Path.fromString(localFileName.substring(0, localFileName.lastIndexOf("/")))

    info("tempLocation " + tempLocation)

    val filesAndDirs = tempLocation  * ((p: Path) => !isJunkFile(settings, p.name))

    val (dirs, files) = filesAndDirs.toSet.toSeq.partition(_.isDirectory)


    println("**********************************Dirs " + dirs)
    info("Files " + files)

//    val files = mount * ((p: Path) => !isJunkFile(settings, p.name))
//    f(files.toSet.toSeq)
//
//    val (dirs, files) = TrueCryptedPartition.listTopLevel(settings, unit.src, tempMountPoint, certificate, passphrase)(_.partition(_.isDirectory))
//    //update the unit

   // case class UploadedUnit(uid: UnitUID, interface: Interface, src: Source, label: Label, size: Bytes, timestamp: Milliseconds, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends ElectronicAssemblyUnit with EncryptedDRIUnit {


      unit = this.unit.copy(  parts = Option(dirs.map(_.name)), orphanedFiles = Option(files.map(_.name)))


  }
//
//    TrueCryptedPartition.getVolumeLabel(settings, unit.src, certificate, passphrase).map {
//      volumeLabel =>
//
//        //extract parts and orphaned files
//        tempMountPoint(username, unit.src) match {
//          case Left(ioe) =>
//            error(s"Unable to update decrypted detail for unit: ${unit.uid}", ioe)
//            false
//          case Right(tempMountPoint) =>
//            val (dirs, files) = TrueCryptedPartition.listTopLevel(settings, unit.src, tempMountPoint, certificate, passphrase)(_.partition(_.isDirectory))
//            //update the unit
//            this.unit = this.unit.copy(partition = this.unit.partition.copy(partitionLabel = Option(volumeLabel)), parts = Option(dirs.map(_.name)), orphanedFiles = Option(files.map(_.name)))
//            true
//        }
//    }.getOrElse(false)

  def updateDecryptDetail(username: String,passphrase: String): Unit = ???

}
