package uk.gov.tna.dri.preingest.loader.unit

object DRIUnit {
  type UnitUID = String
  type Interface = String
  type Source = String
  type Label = String
  type Size = Long
  type Timestamp = Long
}

trait DRIUnit {
  def uid: DRIUnit.UnitUID
  def interface: DRIUnit.Interface
  def src: DRIUnit.Source
  def label: DRIUnit.Label
  def size: DRIUnit.Size
  def timestamp: DRIUnit.Timestamp
}

trait EncryptedDRIUnit extends DRIUnit {
  //TODO
}