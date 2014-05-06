package uk.gov.tna.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import akka.actor.{Props, Actor}
import uk.gov.tna.dri.preingest.loader.Settings
import uk.gov.tna.dri.preingest.loader.unit.{DeRegisterUnit, RegisterUnit}
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.{DeviceAdded, DeviceRemoved}
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DeviceFile, PartitionProperties, DiskProperties}
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

class UDisksUnitMonitor extends Actor with Logging {

  private val settings = Settings(context.system)
  lazy val udisks = new UDisksMonitor(settings, self)

  var knownDisks = Map.empty[DeviceFile, DiskProperties]
  var knownPartitions = Map.empty[DeviceFile, UnitUID]

  override def preStart() {
    //get initial devices
    udisks.getAttachedDevices()
  }

  override def postStop() {
    //shutdown UDisks DBus connection
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
          val (unit, unitActor) = if(!partitionProperties.lvmDevice && partitionProperties.mounted.isEmpty) {
            val unit = new TrueCryptedPartitionUnit(partitionProperties, diskProperties)
            (unit, () => new TrueCryptedPartitionUnitActor(unit))
          } else {
            val unit = new NonEncryptedPartitionUnit(partitionProperties, diskProperties)
            (unit, () => new NonEncryptedPartitionUnitActor(unit))
          }

          val unitActorRef = context.actorOf(Props(unitActor))
          this.knownPartitions += (partitionProperties.deviceFile -> unit.uid)
          context.parent ! RegisterUnit(unit.uid, unitActorRef)

        case None =>
          error("Could not find DiskProperties for Partition: " + partitionProperties.deviceFile)
      }

    //received from DBusUDisks on DeviceRemoved
    case DeviceRemoved(deviceFile) =>
      //is known disk?
      if(this.knownDisks.contains(deviceFile)) {
        //remove from known disks
        this.knownDisks = this.knownDisks.filterNot(kv => kv._1 == deviceFile)

      //is known partition?
      } else if(this.knownPartitions.contains(deviceFile)) {
        //inform others that a Unit (Partition is no longer available)
        context.parent ! DeRegisterUnit(this.knownPartitions(deviceFile))

        //remove from known partitions
        this.knownPartitions = this.knownPartitions.filterNot(kv => kv._1 == deviceFile)
      }
  }

  /**
   * Finds the Disk for a Partition
   */
  def findDisk(partitionProperties: PartitionProperties) : Option[DiskProperties] = {
    knownDisks.find(kv => partitionProperties.deviceFile.startsWith(kv._1)).map(_._2)
  }
}