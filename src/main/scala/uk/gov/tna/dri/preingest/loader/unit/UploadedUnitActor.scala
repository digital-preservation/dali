package uk.gov.tna.dri.preingest.loader.unit

import akka.actor.{ActorRef, Actor}
import grizzled.slf4j.Logging
import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

case class UploadedUnit(uid: DRIUnit.UnitUID, interface: DRIUnit.Interface, src: DRIUnit.Source, label: DRIUnit.Label, size: DRIUnit.Size, timestamp: DRIUnit.Timestamp) extends DRIUnit

class UploadedUnitActor(uid: UnitUID, unit: Path) extends Actor with Logging {

  def receive = {
    case SendUnitStatus(listener: ActorRef) =>
       listener ! UploadedUnit(uid, UploadedUnitMonitor.NETWORK_INTERFACE, UploadedUnitMonitor.UPLOAD_LOCATION, unit.name, unit.size.get, unit.lastModified)
  }
}
