package uk.gov.tna.dri.preingest.loader.unit

import akka.actor.{ActorLogging, Props, Actor}
import scala.concurrent.duration._
import grizzled.slf4j.Logging
import scala.collection.mutable

case class Register(pendingUnit: PendingUnit)
case class DeRegister(pendingUnit: PendingUnit)
case object ListPendingUnits

class PendingUnitsActor extends Actor with Logging {

  val uploadedUnitMonitor = context.actorOf(Props[UploadedUnitMonitor], name="UploadedUnitMonitor")

  import context.dispatcher

  context.system.scheduler.schedule(100 milliseconds, 5 minutes, uploadedUnitMonitor, ScheduledExecution)
  info("Scheduled: " + uploadedUnitMonitor.path)

  val udisksUnitMonitor = context.actorOf(Props[UDisksUnitMonitor], name="UDisksUnitMonitor")


  def receive = {

    case ListPendingUnits =>



  }
}
