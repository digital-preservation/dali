package uk.gov.tna.dri.preingest.loader

import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

object ClientAction {

  case class UnitRef(uid: UnitUID)
  case class TargetUnitPart(unit: UnitUID, series: String, destination: String, fixity: Boolean )
  case class LoadUnit(uid: UnitUID, parts: Seq[TargetUnitPart])
  case class EncryptedUnit(uid: UnitUID, encryptionMethod: Option[String])
  case class Actions(actions: List[Action])
  case class Action(action: String, limit: Option[Int], unitRef: Option[UnitRef], loadUnit: Option[LoadUnit], encryptedUnit: Option[EncryptedUnit], certificate: Option[String], passphrase: Option[String])

}

