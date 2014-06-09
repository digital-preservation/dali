package uk.gov.tna.dri.preingest.loader

import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

object ClientAction {

  case class UnitRef(uid: UnitUID)
  case class TargetUnitPart(unit: UnitUID, series: String, destination: String )
  case class LoadUnit(uid: UnitUID, parts: Seq[TargetUnitPart])
  case class Actions(actions: List[Action])
  case class Action(action: String, limit: Option[Int], unitRef: Option[UnitRef], unit: Option[LoadUnit], certificate: Option[String], passphrase: Option[String])

}

