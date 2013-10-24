package uk.gov.tna.dri.preingest.loader.unit

case class Part(unitId: String, series: String)

trait PartSource {
  def listParts : List[Part]
}
