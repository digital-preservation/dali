package uk.gov.tna.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import akka.actor.{Props, Actor}
import uk.gov.tna.dri.preingest.loader.unit.{DeRegisterUnit, RegisterUnit}
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.{DeviceAdded, DeviceRemoved}


class UDisksUnitMonitor extends Actor with Logging {

  lazy val udisks = new UDisksMonitor(this.self)

  override def preStart() {
    //get initial devices
    udisks.getAttachedDevices()
  }

  override def postStop() {
    udisks.close()
  }

  def receive = {
    //received from DBusUDisks on DeviceAdded
    case DeviceAdded(storeProperties) =>
      //this.known = r.pendingUnit :: this.known
      val unitActor = context.actorOf(Props(new PhysicalUnitActor()))
      context.parent ! RegisterUnit(storeProperties.deviceFile, unitActor)

    //received from DBusUDisks on DeviceRemoved
    case DeviceRemoved(deviceFile) =>
      //this.known = this.known.filterNot(_.src == d.pendingUnit.src)
      context.parent ! DeRegisterUnit(deviceFile)
  }
}