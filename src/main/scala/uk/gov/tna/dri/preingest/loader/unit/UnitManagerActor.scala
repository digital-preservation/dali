package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.{Props, ActorRef, Actor}
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID
import uk.gov.tna.dri.preingest.loader.unit.disk.UDisksUnitMonitor

case class RegisterUnit(unitUid: UnitUID, unit: ActorRef)
case class DeRegisterUnit(unitUid: UnitUID)
case class SendUnitStatus(listener: ActorRef)
case class RemoveUnit(unitUid: UnitUID)

class UnitManagerActor extends Actor with Logging {

  lazy val uploadedUnitMonitor = context.actorOf(Props[UploadedUnitMonitor], name="UploadedUnitMonitor")
  import context.dispatcher
  import scala.concurrent.duration._
  context.system.scheduler.schedule(5 seconds, 30 seconds, uploadedUnitMonitor, ScheduledExecution) //TODO make configurable
  info("Scheduled: " + uploadedUnitMonitor.path)

  lazy val udisksUnitMonitor = context.actorOf(Props[UDisksUnitMonitor], name="UDisksUnitMonitor")

  //state
  var units = Map.empty[UnitUID, ActorRef]
  var listeners = List.empty[ActorRef]

  def receive = {

    case Listen =>
      this.listeners = sender :: listeners

      //TODO
    //case ListUnits =>


    case RegisterUnit(unitId, unit) =>
      this.units = units + (unitId -> unit)
      listeners.map {
        unit ! SendUnitStatus(_)
      }

     //TODO
     //case UnitUpdated(unitId) =>
     // listeners.map {
     //   listeners(unitId) ! SendStatus(_)
     // }

    case DeRegisterUnit(unitId) =>
      context.stop(units(unitId)) //shutdown the unit actor

      //remove our knowledge of the actor
      this.units = units.filterNot(kv => kv._1 == unitId)

      //notify listeners that the Unit is no longer available
      listeners.map {
        _ ! RemoveUnit(unitId)
      }

  }
}
