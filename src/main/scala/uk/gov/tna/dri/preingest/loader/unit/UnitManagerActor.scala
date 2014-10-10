package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.{Props, ActorRef, Actor}
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID
import uk.gov.tna.dri.preingest.loader.unit.disk._
import uk.gov.tna.dri.preingest.loader.UserErrorMessages._
import uk.gov.tna.dri.preingest.loader.Settings
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DiskProperties, PartitionProperties}
import scala.Some
import uk.gov.tna.dri.preingest.loader.unit.disk.EncryptedPartitionUnit
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.DiskProperties
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.PartitionProperties
import uk.gov.tna.dri.preingest.loader.unit.EncryptionUnitType.EncryptionUnitType

case class UnitAction(progress: Int)

case object Listen
case class RegisterUnit(unitUid: UnitUID, unit: ActorRef)
case class DeRegisterUnit(unitUid: UnitUID)
case class SendUnitStatus(listener: ActorRef, clientId: Option[String] = None)
case class UnitStatus(unit: DRIUnit, action: Option[UnitAction] = None, clientId: Option[String] = None)
case class UnitProgress(unit: DRIUnit, parts: Seq[TargetedPart], progressPercentage: Integer)
case class PartFixityProgress(part: Part, progressPercentage: Integer)
case class PartFixityError(part: Part, errorMessage: String)
case class PartsCatalogued(uid: String, parts: Seq[Part])
//case class UnitError(unit: DRIUnit, errorMessage: String)
case class RemoveUnit(unitUid: UnitUID)
case class ListUnits(clientId: String)
case class GetLoaded(limit: Int)
case class LoadUnit(username: String, unitUid: UnitUID, parts: Seq[TargetedPart], certificate: Option[String], passphrase: Option[String], clientId: Option[String], unitManager: Option[ActorRef])
case class UpdateUnitDecryptDetail(username: String, uid: UnitUID, certificate: Option[String], passphrase: String, clientId: Option[String] = None)
case class UpdateDecryptDetail(username: String, listener: ActorRef, certificate: Option[String], passphrase: String, clientId: Option[String])
case class ReplaceDecryptMethod(unitId: UnitUID, method: Option[String])
case class UserProblemNotification(errorMsg: UserErrorMessage, clientId: Option[String])
case class SendPartitionDetails(manager: ActorRef, unitId: UnitUID, encryptionMethod: Option[String])
case class PartitionDetailsForEncryptionMethodChange(uid: UnitUID, encryptionMethod: Option[String], unit: PartitionUnit)

object EncryptionUnitType extends Enumeration {
  type EncryptionUnitType = Value
  val GenericEncryption = Value("genericEncryption")
  val Truecrypt = Value("truecrypt")
//  val LUKS = Value("luks")

  // ensure the JSON value sent from the interface makes sense
  def getOption(s: Option[String]): Option[Value] = {
    s match {
      case Some(str) =>
        if (values.exists(_.toString == str))
          values.find(_.toString == str)
        else
          None
      case None => // revert to default unspecified method
        Some(EncryptionUnitType.GenericEncryption)
    }
  }
}




class UnitManagerActor extends Actor with Logging {

  private val settings = Settings(context.system)

  private val uploadedUnitMonitor = context.actorOf(Props[UploadedUnitMonitor], name="UploadedUnitMonitor")


  import context.dispatcher
  import scala.concurrent.duration._
  import EncryptionUnitType._

  context.system.scheduler.schedule(settings.Unit.uploadedScheduleDelay, settings.Unit.uploadedScheduleFrequency, uploadedUnitMonitor, ScheduledExecution)
  info("Scheduled: " + uploadedUnitMonitor.path)

  // this val may appear unused but is essential to start the dbus monitor - do not delete
  private val udisksUnitMonitor = context.actorOf(Props[UDisksUnitMonitor], name="UDisksUnitMonitor")

  //state
  private var units = Map.empty[UnitUID, ActorRef]
  private var listeners = List.empty[ActorRef]

  def receive = {

    case Listen =>
      this.listeners = sender :: listeners
      info("UnitManagerActor listen " + listeners)

    case ListUnits(clientId) => //TODO specific client!
      this.units.values.map(_ ! SendUnitStatus(sender, Option(clientId)))
      info("UnitManagerActor listUnits clientId" + clientId)

    // route a decrypt request to the correct Actor
    case UpdateUnitDecryptDetail(username, unitUid, certificate, passphrase, clientId) =>
      val unitActor = this.units(unitUid)
      info("UnitManagerActor UpdateUnitDecryptDetail clientId " + clientId + " unitUid " + unitUid + "unitActor " + unitActor )
      unitActor ! UpdateDecryptDetail(username, sender, certificate, passphrase, clientId)

    // replace the Actor given a change in decryption method
    case ReplaceDecryptMethod(unitId, method) =>
      val oldUnitActor = this.units(unitId)
      // need to get the disk details before doing the replacement
      // will get call-back to PartitionDetailsForEncryptionMethodChange
      oldUnitActor !  SendPartitionDetails(self, unitId, method)

    case  PartitionDetailsForEncryptionMethodChange(uid, encryptionMethod, oldUnit)  =>
      // create the new actor from the method string, and populate it with the disk details from the old unit
      // then deregister the old actor and register the new one
      val mediaProps = oldUnit.getProperties
      val partition = mediaProps._1
      val disk = mediaProps._2
      EncryptionUnitType.getOption(encryptionMethod) match {
        case Some(eV) =>
          val (newUnit, newUnitActor) = getUnitFromType(eV, partition, disk)
          self ! DeRegisterUnit(newUnit.uid)
          val newUnitActorRef = context.actorOf(newUnitActor)
          self ! RegisterUnit(newUnit.uid, newUnitActorRef)
          info("UnitManagerActor ReplaceDecryptMethod unitId " + oldUnit.uid + " method " + encryptionMethod )
        case None =>
          // leave old Actor unchanged
            error("Encryption method " + encryptionMethod.toString + " has not yet been implemented")
      }



    case RegisterUnit(unitId, unit) =>
      this.units = units + (unitId -> unit)
      listeners.map {
        unit ! SendUnitStatus(_)
      }
      info("UnitManagerActor RegisterUnit unitId " + unitId + " unit " + (unitId -> unit) )



    case DeRegisterUnit(unitId) =>
      context.stop(units(unitId)) //shutdown the unit actor

      //remove our knowledge of the actor
      this.units = units.filterNot(kv => kv._1 == unitId)

      //notify listeners that the Unit is no longer available
      listeners.map {
        _ ! RemoveUnit(unitId)
      }
      info("UnitManagerActor DeRegisterUnit unitId " + unitId )


    case LoadUnit(username, unitUid, parts, certificate, passphrase, clientId, unitManager) =>
      val unitActor = this.units(unitUid)
      unitActor ! Load(username, parts, certificate, passphrase, clientId, Some(self))

    case ue: UnitError =>
      listeners.map(_ ! ue)

    case pm: UnitProgress =>
      listeners.map(_ ! pm)

    case fpm: PartFixityProgress =>
      listeners.map(_ ! fpm)

    case fue: PartFixityError =>
      listeners.map(_ ! fue)
  }

  // at this point, we are guaranteed that unitType exists and is valid
  // however, this depends on getUnitFromType and EncryptionUnitType being kept in sync when new encryption types are
  // added. Would be better to do this object instantiation by introspection, and if you can get scala to do that, please do
  def getUnitFromType(unitType: EncryptionUnitType.Value, partition: PartitionProperties, disk: DiskProperties): (DRIUnit, Props) = {
    unitType match {
      case Truecrypt =>
        val newUnit = new TrueCryptedPartitionUnit(partition, disk)
        val newUnitActor = TrueCryptedPartitionUnitActor.props(newUnit)
        (newUnit, newUnitActor)
      case GenericEncryption =>
        // regressing to stublike EncryptedUnitActor
        val newUnit = new EncryptedPartitionUnit(partition, disk)
        val newUnitActor = EncryptedPartitionUnitActor.props(newUnit)
        (newUnit, newUnitActor)
    }

  }
}
