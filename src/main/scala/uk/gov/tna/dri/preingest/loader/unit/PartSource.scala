package uk.gov.tna.dri.preingest.loader.unit



trait PartSource {
  def listParts : List[Part]
}
