package uk.gov.tna.dri.preingest.loader.unit

/**
 * @param size Size of the Unit in bytes
 * @param timestamp milliseconds since the epoch
 */

//@deprecated //TODO delete?
//case class PendingUnit(interface: String, src: String, label: String, size: Option[Long], timestamp: Option[Long], parts: Option[Seq[Part]] = None)
case class Part(unitId: String, series: String)

object Destination extends Enumeration {
  type Destination = Value
  val Holding = Value("Holding")
  val PreIngest = Value("Pre-Ingest")
  val Holding_Sandbox = Value("Holding + Sandbox")
  // FIXME there is surely a nicer way to do this?
  def invert(value: Value): List[String] = value.toString match {
    //at the moment catalogue supports only these sets (DriCataloguePreingestListener.java must be ammended to support others)
    case "Holding" => List("Holding")
    case "Pre-Ingest" => List("PreIngest")
    case "Holding + Sandbox" => List("Holding", "Sandbox")
  }

}


case class TargetedPart(destination: Destination.Destination, part: Part)
