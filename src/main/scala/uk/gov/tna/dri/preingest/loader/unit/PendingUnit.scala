package uk.gov.tna.dri.preingest.loader.unit

/**
 * @param size Size of the Unit in bytes
 * @param timestamp milliseconds since the epoch
 */
case class PendingUnit(interface: String, src: String, label: String, size: Option[Long], timestamp: Option[Long], parts: Option[Seq[Part]] = None)
