package uk.gov.tna.dri.preingest.loader.unit

import org.json4s.JObject


object DRIUnit {
  type UnitUID = String
  type UnitType = String
  type Encrypted = Boolean
  type Interface = String
  type Source = String
  type Label = String
  type Bytes = Long
  type Milliseconds = Long
}

trait DRIUnit {
  import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._

  def uid: UnitUID
  def unitType: UnitType
  def encrypted: Encrypted
  def interface: Interface
  def src: Source
  def label: Label
  def size: Bytes
  def timestamp: Milliseconds

  def toJson() : JObject = {
    import org.json4s.JsonDSL._

    ("uid" -> uid) ~
    ("type" -> unitType) ~
    ("encrypted" -> encrypted) ~
    ("interface" -> interface) ~
    ("src" -> src) ~
    ("label" -> label) ~
    ("size" -> size) ~
    ("timestamp" -> timestamp)
  }
}

trait EncryptedDRIUnit extends DRIUnit {    //partition or disk? does it matter (probably not!)
  val encrypted = true
}

trait PhysicalMediaUnit extends DRIUnit

trait ElectronicAssemblyUnit extends DRIUnit