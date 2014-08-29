package uk.gov.tna.dri.preingest.loader.unit

import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._
import akka.actor.ActorRef
import uk.gov.tna.dri.preingest.loader.{UserErrorMessages, PreIngestLoaderActor}
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DiskProperties, PartitionProperties}
import uk.gov.tna.dri.preingest.loader.certificate._
import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.unit.network.{GlobalUtil, RemotePath, GPGCrypt, RemoteStore}
import uk.gov.tna.dri.preingest.loader.store.DataStore
import uk.gov.tna.dri.preingest.loader.unit.common.MediaUnitActor
import uk.gov.tna.dri.preingest.loader.unit.common.unit.isJunkFile

case class UploadedUnit(uid: UnitUID, interface: Interface, src: Source, label: Label, size: Bytes, timestamp: Milliseconds, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None)
      extends EncryptedDRIUnit with MediaUnit {
  def unitType = "Uploaded"
  override def humanId = label
}

class UploadedUnitActor(val uid: DRIUnit.UnitUID, val unitPath: RemotePath) extends EncryptedDRIUnitActor[UploadedUnit] with MediaUnitActor[UploadedUnit] with Logging{

  val opts  = RemoteStore.createOpt(settings.Unit.sftpServer, settings.Unit.username, settings.Unit.certificateFile, settings.Unit.timeout)
  var unit = UploadedUnit(uid, settings.Unit.uploadedInterface, settings.Unit.uploadedSource.path, unitPath.fileName, unitPath.fileSize, unitPath.lastModified)

  //val remoteFileName = s"""${unit.src}/${unitPath.name}.${settings.Unit.uploadedGpgZipFileExtension}"""
  val remoteFileName = s"""${unit.src}/${unitPath.path}"""

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests
  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], clientSender: Option[ActorRef]) {
    println("ld it should first copy the data in order to decrypt it ")
  }

  def copyData(username: String, parts: Seq[uk.gov.tna.dri.preingest.loader.unit.TargetedPart],certificate: (uk.gov.tna.dri.preingest.loader.certificate.CertificateName,
    uk.gov.tna.dri.preingest.loader.certificate.CertificateData),passphrase: Option[String],unitManager: Option[akka.actor.ActorRef]): Unit = {
      copyData(username, parts, unitManager)
      GlobalUtil.cleanupProcessing(opts, getLoadFile(unit.label))
  }

  //creates a file "loading" to mark progress, copies the file locally, decrypts it and send the unit.parts to review
  def updateDecryptDetail(username: String, listener:ActorRef, certificate: CertificateDetail, passphrase: String) {

    //create load file, mark processing = true
    GlobalUtil.initProcessing(opts, getLoadFile(remoteFileName))

    //extract parts and orphaned files
    tempMountPoint(username, unit.label) match {
      case Left(ioe) =>

        GlobalUtil.cleanupProcessing(opts, getLoadFile(remoteFileName))
        listener ! UnitError(unit, "Unable to decrypt data for unit: " + remoteFileName)
        error(s"Unable to mount unit: ${unit.uid}", ioe)

      case Right(tempMountPointPath) =>
        val localFileName = tempMountPointPath.path + "/"  + unitPath.fileName + settings.Unit.uploadedGpgZipFileExtension
        //copy the file locally
        info("ld receiving file " + remoteFileName + " and copying to " + localFileName)
        try {
          RemoteStore.receiveFile(opts, remoteFileName, localFileName)
        } catch {
          case e: Exception =>
            GlobalUtil.cleanupProcessing(opts, getLoadFile(remoteFileName))
            listener ! UnitError(unit, "Unable to copy data for unit: " + remoteFileName + " to " + localFileName + "error "+ e.getMessage)
        }

        //decrypt the file
        val decryptCode =
        DataStore.withTemporaryFile(Option(certificate)) {
          cert =>
            GPGCrypt.decryptAndUnzip(localFileName, cert, passphrase )
        }

        if (decryptCode != 0) {
          GlobalUtil.cleanupProcessing(opts, getLoadFile(remoteFileName))
          listener ! UnitError(unit, "Unable to decrypt data for unit: " + remoteFileName)
        }

        //extract dirs and files info
        //add the unit folder

        val partsLocationPath = tempMountPointPath / unit.label
        val filesAndDirs = partsLocationPath * ((p: Path) => !isJunkFile(settings, p.name))
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
          case Some(sender) =>
            sender ! UnitError(unit, "Unable to copy data for unit! :" + ioe.getMessage)
            GlobalUtil.cleanupProcessing(opts, getLoadFile(remoteFileName))
          case None =>
        }

      case Right(mountPoint) =>
          copyFiles( parts, mountPoint / unit.label,  unitManager)
    }
  }

  private def getLoadFile(fileName: String): String =  {
    val loadingExtension = settings.Unit.loadingExtension
    s"$fileName.$loadingExtension"
  }



}