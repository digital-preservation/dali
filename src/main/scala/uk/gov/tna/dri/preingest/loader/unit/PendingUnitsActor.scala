package uk.gov.tna.dri.preingest.loader.unit

import akka.actor.{ActorRef, Props, Actor}
import scala.concurrent.duration._
import grizzled.slf4j.Logging
import akka.util.Timeout
import uk.gov.tna.dri.preingest.loader.certificate.CertificateDetail
import uk.gov.tna.dri.preingest.loader.io.UnitLoadStatus
import akka.routing.SmallestMailboxRouter
import uk.gov.tna.dri.preingest.loader.unit.disk.UDisksUnitMonitor

case class Register(pendingUnit: PendingUnit)
case class DeRegister(pendingUnit: PendingUnit)
case class ListPendingUnits(clientId: String)
case class PendingUnits(clientId: String, pendingUnits: List[PendingUnit])
case class DecryptUnit(username: String, pendingUnit: PendingUnit, certificate: Option[CertificateDetail], passphrase: Option[String])

case object Listen



@deprecated //TODO delete
class PendingUnitsActor extends Actor with Logging {

  lazy val uploadedUnitMonitor = context.actorOf(Props[UploadedUnitMonitor], name="UploadedUnitMonitor")
  import context.dispatcher
  context.system.scheduler.schedule(5 seconds, 30 seconds, uploadedUnitMonitor, ScheduledExecution) //TODO make configurable
  info("Scheduled: " + uploadedUnitMonitor.path)

  val udisksUnitMonitor = context.actorOf(Props[UDisksUnitMonitor], name="UDisksUnitMonitor")

  //val LOADER_COUNT = Runtime.getRuntime.availableProcessors() //TODO make configurable
  //lazy val loadActor = context.actorOf(Props[LoadActor].withRouter(SmallestMailboxRouter(LOADER_COUNT)), "LoadActorRouter")

  //mutable
  var listeners = List.empty[ActorRef]


  def receive = {

    case Listen =>
       listeners = sender :: listeners

    case ListPendingUnits(clientId) =>
      import akka.pattern.{ask, pipe}

      implicit val timeout = Timeout(20 seconds) //udisks queries can be slow! TODO make configurable

      //TODO
//      val f = for {
//          pau <- (udisksUnitMonitor ? ListPendingUnits).mapTo[PendingAttachedUnits]
//          puu <-(uploadedUnitMonitor ? ListPendingUnits).mapTo[PendingUploadedUnits]
//        } yield PendingUnits(clientId, pau.pending ++ puu.pending)
//        f pipeTo sender

    case r: Register =>
      debug("Registered: " + r.pendingUnit.src)
      broadcast(r)

    case d: DeRegister =>
      broadcast(d)

    case du: DecryptUnit =>
      //route the message to the appropriate interface source
      du.pendingUnit.interface match {
        case UploadedUnitMonitor.NETWORK_INTERFACE =>
          uploadedUnitMonitor ! du
        case _ =>
          udisksUnitMonitor ! du
      }

    case lu: LoadUnit =>
      //loadActor ! lu

    case uls: UnitLoadStatus =>
      broadcast(uls)
      //route the message to the appropriate interface source
      /* lu.loadingUnit.interface match {
        case UploadedUnitMonitor.NETWORK_INTERFACE =>
          uploadedUnitMonitor ! lu
        case _ =>
          udisksUnitMonitor ! lu
      } */
  }

  def broadcast(msg: Any) {
    for(listener <- listeners) {
      listener ! msg
    }
  }
}
