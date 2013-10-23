package uk.gov.tna.dri.preingest.loader.unit

import akka.actor.{ActorRef, Props, Actor}
import scala.concurrent.duration._
import grizzled.slf4j.Logging
import akka.util.Timeout
import scalax.file.Path

case class Register(pendingUnit: PendingUnit)
case class DeRegister(pendingUnit: PendingUnit)
case class ListPendingUnits(clientId: String)
case class PendingUnits(clientId: String, pendingUnits: List[PendingUnit])
case class DecryptUnit(pendingUnit: PendingUnit, certificate: Option[Path], passphrase: Option[String])
case object Listen

class PendingUnitsActor extends Actor with Logging {

  val uploadedUnitMonitor = context.actorOf(Props[UploadedUnitMonitor], name="UploadedUnitMonitor")

  import context.dispatcher

  context.system.scheduler.schedule(5 seconds, 30 seconds, uploadedUnitMonitor, ScheduledExecution) //TODO make configurable
  info("Scheduled: " + uploadedUnitMonitor.path)

  val udisksUnitMonitor = context.actorOf(Props[UDisksUnitMonitor], name="UDisksUnitMonitor")

  //mutable
  var listeners = List.empty[ActorRef]


  def receive = {

    case Listen =>
       listeners = sender :: listeners

    case ListPendingUnits(clientId) =>
      import akka.pattern.{ask, pipe}

      implicit val timeout = Timeout(20 seconds) //udisks queries can be slow! TODO make configurable

      val f = for {
          pau <- (udisksUnitMonitor ? ListPendingUnits).mapTo[PendingAttachedUnits]
          puu <-(uploadedUnitMonitor ? ListPendingUnits).mapTo[PendingUploadedUnits]
        } yield PendingUnits(clientId, pau.pending ++ puu.pending)
        f pipeTo sender

    case r: Register =>
      for(listener <- listeners) {
        listener ! r
      }

    case d: DeRegister =>
      for(listener <- listeners) {
        listener ! d
      }

    case du: DecryptUnit =>
      //route the message to the appropriate interface source
      du.pendingUnit.interface match {
        case UploadedUnitMonitor.NETWORK_INTERFACE =>
          uploadedUnitMonitor ! du
        case _ =>
          udisksUnitMonitor ! du
      }
  }
}
