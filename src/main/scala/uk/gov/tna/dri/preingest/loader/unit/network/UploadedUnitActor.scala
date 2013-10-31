package uk.gov.tna.dri.preingest.loader.unit

import scalax.file.Path

case class UploadedUnit(uid: DRIUnit.UnitUID, interface: DRIUnit.Interface, src: DRIUnit.Source, label: DRIUnit.Label, size: DRIUnit.Bytes, timestamp: DRIUnit.Milliseconds) extends ElectronicAssemblyUnit {
  def unitType = "Uploaded"
  def encrypted = false
}

class UploadedUnitActor(val uid: DRIUnit.UnitUID, val unitPath: Path) extends DRIUnitActor[UploadedUnit] {

  def unit = UploadedUnit(uid, UploadedUnitMonitor.NETWORK_INTERFACE, UploadedUnitMonitor.UPLOAD_LOCATION, unitPath.name, unitPath.size.get, unitPath.lastModified)

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests
  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String]) = ???

  def copyData(username: String, parts: Seq[TargetedPart], certificate: _root_.uk.gov.tna.dri.preingest.loader.certificate.CertificateDetail, passphrase: Option[String]) = ???
}
