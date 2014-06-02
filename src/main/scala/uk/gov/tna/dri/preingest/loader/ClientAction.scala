package uk.gov.tna.dri.preingest.loader

import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

object ClientAction {

  case class UnitRef(uid: UnitUID)
  case class TargetUnitPart(unit: UnitUID, series: String, destination: String )
  case class LoadUnit(uid: UnitUID, parts: Seq[TargetUnitPart])

  case class Actions(actions: List[Action])
  case class Action(action: String, limit: Option[Int], unitRef: Option[UnitRef], loadUnit: Option[LoadUnit], certificate: Option[String], passphrase: Option[String])
  //case class Pending(action: String) extends Action(action, None, None, None, None)

  //isHacky - needed to disambiguate between decrypt and load messages on the server side
  //case class Load(action: String, isHacky: Boolean, unit: LoadUnit, certificate: Option[String], passphrase: Option[String]) extends Action(action)
  //case class LoadUnit(interface: String, src:String, label:String,  parts: Seq[TargetUnitPart])


 // case class Actions(actions: List[Action])
}

//object MyClientAction {
   //
//  case class

  //case class

//}
