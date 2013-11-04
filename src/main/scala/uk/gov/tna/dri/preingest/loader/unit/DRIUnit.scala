package uk.gov.tna.dri.preingest.loader.unit

import org.json4s.JObject
import org.json4s.JsonAST.JNull


object DRIUnit {
  type UnitUID = String
  type UnitType = String
  type Encrypted = Boolean
  type Interface = String
  type Source = String
  type Label = String
  type Bytes = Long
  type Milliseconds = Long
  type PartName = String
  type OrphanedFileName = String
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

  /**
   * Some sort of identifier that can be recognised by humans
   */
  def humanId: String

  /**
   * Parts are top-level folders
   * within the Unit
   *
   * Parts may be None if the part information for
   * the unit cannot be established (i.e. because it is still encrypted),
   * otherwise it is a sequence of zero of more top-level folder names
   */
  def parts: Option[Seq[PartName]]

  /**
   * Orphaned Files are top-level files
   * within a Unit (i.e. these should not be present in a valid Unit)
   *
   * Orphaned Files may be None of the information
   * for the unit cannot be established (i.e. because it is still encrypted),
   * otherwise it is a sequence of zero of more top-level file names
   */
  def orphanedFiles : Option[Seq[OrphanedFileName]]



  def toJson() : JObject = {
    import org.json4s.JsonDSL._

    ("uid" -> uid) ~
    ("type" -> unitType) ~
    ("encrypted" -> encrypted) ~
    ("interface" -> interface) ~
    ("src" -> src) ~
    ("label" -> label) ~
    ("size" -> size) ~
    ("timestamp" -> timestamp) ~
    ("parts" -> parts) ~
    ("orphanedFiles" -> orphanedFiles)
  }
}

trait NonEncryptedDRIUnit extends DRIUnit {
  val encrypted = false
}

trait EncryptedDRIUnit extends DRIUnit {
  val encrypted = true
}

trait PhysicalMediaUnit extends DRIUnit {
  def humanId = src
}

trait ElectronicAssemblyUnit extends DRIUnit