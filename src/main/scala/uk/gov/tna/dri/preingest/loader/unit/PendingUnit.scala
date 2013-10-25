package uk.gov.tna.dri.preingest.loader.unit

/**
 * @param size Size of the Unit in bytes
 * @param timestamp milliseconds since the epoch
 */
case class PendingUnit(interface: String, src: String, label: String, size: Option[Long], timestamp: Option[Long], parts: Option[Seq[Part]] = None)
case class Part(unitId: String, series: String)

object Destination extends Enumeration {
  type Destination = Value
  val Holding = Value("Holding")
  val PreIngest = Value("Pre-Ingest")
  val Holding_Sandbox = Value("Holding + Sandbox")
}

case class LoadingUnit(interface: String, src: String, label: String, parts: Seq[TargetedPart])
case class TargetedPart(destination: Destination.Destination, part: Part)
