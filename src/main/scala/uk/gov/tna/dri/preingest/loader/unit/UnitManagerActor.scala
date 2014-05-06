package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.{Props, ActorRef, Actor}
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID
import uk.gov.tna.dri.preingest.loader.unit.disk.UDisksUnitMonitor
import uk.gov.tna.dri.preingest.loader.UserErrorMessages._
import uk.gov.tna.dri.preingest.loader.Settings
import uk.gov.tna.dri.preingest.loader.PreIngestLoaderActor

case class UnitAction(progress: Int)

case object Listen
case class RegisterUnit(unitUid: UnitUID, unit: ActorRef)
case class DeRegisterUnit(unitUid: UnitUID)
case class SendUnitStatus(listener: ActorRef, clientId: Option[String] = None)
case class UnitStatus(unit: DRIUnit, action: Option[UnitAction] = None, clientId: Option[String] = None)
case class UnitProgress(unit: DRIUnit, progressPercentage: Integer)
//case class UnitError(unit: DRIUnit, errorMessage: String)
case class RemoveUnit(unitUid: UnitUID)
case class ListUnits(clientId: String)
case class LoadUnit(username: String, unitUid: UnitUID, parts: Seq[TargetedPart], certificate: Option[String], passphrase: Option[String], clientId: Option[String], unitManager: Option[ActorRef])
case class UpdateUnitDecryptDetail(username: String, uid: UnitUID, certificate: Option[String], passphrase: String, clientId: Option[String] = None)
case class UpdateDecryptDetail(username: String, listener: ActorRef, certificate: Option[String], passphrase: String, clientId: Option[String])
case class UserProblemNotification(errorMsg: UserErrorMessage, clientId: Option[String])

class UnitManagerActor extends Actor with Logging {

  private val settings = Settings(context.system)

  private val uploadedUnitMonitor = context.actorOf(Props[UploadedUnitMonitor], name="UploadedUnitMonitor")

  import context.dispatcher
  import scala.concurrent.duration._
  context.system.scheduler.schedule(settings.Unit.uploadedScheduleDelay, settings.Unit.uploadedScheduleFrequency, uploadedUnitMonitor, ScheduledExecution)
  info("Scheduled: " + uploadedUnitMonitor.path)

  private val udisksUnitMonitor = context.actorOf(Props[UDisksUnitMonitor], name="UDisksUnitMonitor")

  //state
  private var units = Map.empty[UnitUID, ActorRef]
  private var listeners = List.empty[ActorRef]

  def receive = {

    case Listen =>
      this.listeners = sender :: listeners

    case ListUnits(clientId) => //TODO specific client!
      this.units.values.map(_ ! SendUnitStatus(sender, Option(clientId)))


    case UpdateUnitDecryptDetail(username, unitUid, certificate, passphrase, clientId) =>
//      val unitActor = this.units(unitUid)
//      if(unitActor.isInstanceOf[EncryptedDRIUnitActor[_ <: EncryptedDRIUnit]]) {
//        unitActor ! UpdateDecryptDetail(username, sender, certificate, passphrase, clientId)
//      } else {
//        //cannot decrypt a non-encrypted unit, send error message to client
//        sender ! UserProblemNotification(DECRYPT_NON_ENCRYPTED, clientId)
//      }
        val unitActor = this.units(unitUid)
        unitActor ! UpdateDecryptDetail(username, sender, certificate, passphrase, clientId)


    case RegisterUnit(unitId, unit) =>
      this.units = units + (unitId -> unit)
      listeners.map {
        unit ! SendUnitStatus(_)
      }

     //TODO  above will probably cope with update too!
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

    case LoadUnit(username, unitUid, parts, certificate, passphrase, clientId, unitManager) =>
      val unitActor = this.units(unitUid)
      unitActor ! Load(username, parts, certificate, passphrase, clientId, Some(self))

    case ue: UnitError =>
      listeners.map(_ ! ue)

    case pm: UnitProgress =>
      listeners.map(_ ! pm)
  }
}
