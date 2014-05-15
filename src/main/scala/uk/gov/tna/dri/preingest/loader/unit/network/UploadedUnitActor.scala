package uk.gov.tna.dri.preingest.loader.unit

import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._
import akka.actor.ActorRef
import uk.gov.tna.dri.preingest.loader.PreIngestLoaderActor
import uk.gov.tna.dri.preingest.loader.util.RemotePath

case class UploadedUnit(uid: UnitUID, interface: Interface, src: Source, label: Label, size: Bytes, timestamp: Milliseconds, parts: Option[Seq[PartName]] = None, orphanedFiles: Option[Seq[OrphanedFileName]] = None) extends ElectronicAssemblyUnit with NonEncryptedDRIUnit {
  def unitType = "Uploaded"
  def humanId = label
}

class UploadedUnitActor(val uid: DRIUnit.UnitUID, val unitPath: RemotePath) extends DRIUnitActor[UploadedUnit] {

  var unit = UploadedUnit(uid, settings.Unit.uploadedInterface, settings.Unit.uploadedSource.path, unitPath.name, unitPath.size, unitPath.lastModified)

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests
  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], clientSender: Option[ActorRef]) = ???
}
