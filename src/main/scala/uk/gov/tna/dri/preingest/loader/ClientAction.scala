package uk.gov.tna.dri.preingest.loader

import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

object ClientAction {

  abstract class Action(action: String)

  case class Pending(action: String) extends Action(action)

  case class Decrypt(action: String, unit: UnitRef, certificate: Option[String], passphrase: String) extends Action(action)
  case class UnitRef(uid: UnitUID)

  case class Load(action: String, unit: LoadUnit,  certificate: Option[String], passphrase: Option[String]) extends Action(action)
  case class LoadUnit(interface: String, src:String, label:String,  parts: Seq[TargetUnitPart])
  case class TargetUnitPart(uid: UnitUID, series: String, destination:String )
}
