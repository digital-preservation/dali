package uk.gov.tna.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import akka.actor.{Props, Actor}
import uk.gov.tna.dri.preingest.loader.unit.{DeRegisterUnit, RegisterUnit}
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.{DeviceAdded, DeviceRemoved}
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DeviceFile, PartitionProperties, DiskProperties}


class UDisksUnitMonitor extends Actor with Logging {

  lazy val udisks = new UDisksMonitor(this.self)

  var knownDisks = Map.empty[DeviceFile, DiskProperties]

  override def preStart() {
    //get initial devices
    udisks.getAttachedDevices()
  }

  override def postStop() {
    udisks.close()
  }

  /**
   * At present we assume that only Partitions can be
   * Units and as such we coalesce Partitions to Disks
   */
  def receive = {

    //received from DBusUDisks on DeviceAdded for Disk
    case DeviceAdded(diskProperties: DiskProperties) =>
     this.knownDisks += (diskProperties.deviceFile -> diskProperties) //add to known disks

    //received from DBusUDisks on DeviceAdded for Partition
    case DeviceAdded(partitionProperties: PartitionProperties) =>
      findDisk(partitionProperties) match {

        case Some(diskProperties) =>
          val unitActor: () => Actor = if(!partitionProperties.lvmDevice && partitionProperties.mounted.isEmpty) {
            () => new TrueCryptedPartitionUnitActor(partitionProperties, diskProperties)
          } else {
            () => new UnencryptedPartitionUnitActor(partitionProperties, diskProperties)
          }

          val unitActorRef = context.actorOf(Props(unitActor))
          context.parent ! RegisterUnit(partitionProperties.deviceFile, unitActorRef )

        case None =>
          error("Could not find DiskProperties for Partition: " + partitionProperties.deviceFile)
      }

    //received from DBusUDisks on DeviceRemoved
    case DeviceRemoved(deviceFile) =>
      //is known disk?
      if(this.knownDisks.contains(deviceFile)) {
        //remove from known disks
        this.knownDisks = this.knownDisks.filterNot(kv => kv._1 == deviceFile)
      } else {
        //inform others that a Unit (Partition is no longer available)
        context.parent ! DeRegisterUnit(deviceFile)
      }
  }

  /**
   * Finds the Disk for a Partition
   */
  def findDisk(partitionProperties: PartitionProperties) : Option[DiskProperties] = {
    knownDisks.find(kv => partitionProperties.deviceFile.startsWith(kv._1)).map(_._2)
  }
}