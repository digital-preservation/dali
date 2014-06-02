package uk.gov.tna.dri.preingest.loader.unit

import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._
import akka.actor.ActorRef
import uk.gov.tna.dri.preingest.loader.PreIngestLoaderActor
import uk.gov.tna.dri.preingest.loader.util.RemotePath
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DiskProperties, PartitionProperties}
import uk.gov.tna.dri.preingest.loader.certificate._
import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.unit.network.{GPGCrypt, RemoteStore}
import uk.gov.tna.dri.preingest.loader.store.DataStore
import uk.gov.tna.dri.preingest.loader.unit.common.PhysicalMediaUnitActor
import uk.gov.tna.dri.preingest.loader.unit.common.unit.isJunkFile

case class UploadedUnit(uid: UnitUID, interface: Interface, src: Source, label: Label, size: Bytes, timestamp: Milliseconds, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None)
      extends EncryptedDRIUnit with PhysicalMediaUnit {
  def unitType = "Uploaded"
  override def humanId = label
}

class UploadedUnitActor(val uid: DRIUnit.UnitUID, val unitPath: RemotePath) extends EncryptedDRIUnitActor[UploadedUnit] with PhysicalMediaUnitActor[UploadedUnit] with Logging{

  val opts  = RemoteStore.createOpt(settings.Unit.sftpServer, settings.Unit.username, settings.Unit.certificateFile, settings.Unit.timeout)
  var unit = UploadedUnit(uid, settings.Unit.uploadedInterface, settings.Unit.uploadedSource.path, unitPath.name, unitPath.size, unitPath.lastModified)

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests
  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], clientSender: Option[ActorRef]) {
    println("ld it should first copy the data in order to decrypt it ")
  }

  def copyData(username: String, parts: Seq[uk.gov.tna.dri.preingest.loader.unit.TargetedPart],certificate: (uk.gov.tna.dri.preingest.loader.certificate.CertificateName,
    uk.gov.tna.dri.preingest.loader.certificate.CertificateData),passphrase: Option[String],unitManager: Option[akka.actor.ActorRef]): Unit = {
      copyData(username, parts, unitManager)
      RemoteStore.processing = false
  }

  private def getFileNameFromStringPath(absolutePath: String): String = {
    absolutePath.substring(absolutePath.lastIndexOf("/")+1)
  }

  //creates a file "loading" to mark progress, copies the file locally, decrypts it and send the unit.parts to review
  def updateDecryptDetail(username: String, certificate: CertificateDetail, passphrase: String) {

    RemoteStore.processing = true
    val remoteFileName = unitPath.name
    //create load file
    val loadingExtension = settings.Unit.loadingExtension
    val loadFile = s"$remoteFileName.$loadingExtension"
    RemoteStore.createFile(opts, loadFile)

    //extract parts and orphaned files
    tempMountPoint(username, unit.label) match {
      case Left(ioe) =>
        error(s"Unable to update decrypted detail for unit: ${unit.uid}", ioe)

      case Right(tempMountPointPath) =>
        val localFileName = tempMountPointPath.path +  "/" + getFileNameFromStringPath(remoteFileName)
        //copy the file
        info("ld receiving file " + remoteFileName + " and copying to " + localFileName)
        //todo ld add errror handling for receive
        RemoteStore.receiveFile(opts, remoteFileName, localFileName)

        //descrypt the file
        DataStore.withTemporaryFile(Option(certificate)) {
          cert =>
            GPGCrypt.decryptAndUnzip(localFileName, cert, passphrase )
        }

        //extract dirs and files info
        val filesAndDirs = tempMountPointPath  * ((p: Path) => !isJunkFile(settings, p.name))
        val (dirs, files) = filesAndDirs.toSet.toSeq.partition(_.isDirectory)

        //update unit
        unit = this.unit.copy(parts = Option(dirs.map(_.name)), orphanedFiles = Option(files.map(_.name)))
    }
  }

  def updateDecryptDetail(username: String,passphrase: String): Unit = ???



  private def copyData(username: String, parts: Seq[TargetedPart], unitManager: Option[ActorRef]) {
      //unit.src instead of unit.partition.deviceFile ???
    tempMountPoint(username, unit.label) match {
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

