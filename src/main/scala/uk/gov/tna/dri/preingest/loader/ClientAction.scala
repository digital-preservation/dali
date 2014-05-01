package uk.gov.tna.dri.preingest.loader

import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

object ClientAction {

  abstract class Action(action: String)

  case class Pending(action: String) extends Action(action)

  case class Decrypt(action: String, unit: UnitRef, certificate: Option[String], passphrase: String) extends Action(action)
  case class UnitRef(uid: UnitUID)

  //isHacky - needed to disambiguate between decrypt and load messages on the server side
  case class Load(action: String, isHacky: Boolean, unit: LoadUnit, certificate: Option[String], passphrase: Option[String]) extends Action(action)
  //case class LoadUnit(interface: String, src:String, label:String,  parts: Seq[TargetUnitPart])
  case class LoadUnit(uid: UnitUID, parts: Seq[TargetUnitPart])
  case class TargetUnitPart(unit: UnitUID, series: String, destination: String )
}
