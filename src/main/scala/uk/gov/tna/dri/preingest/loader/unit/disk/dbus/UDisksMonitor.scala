package uk.gov.tna.dri.preingest.loader.unit.disk.dbus

import uk.gov.tna.dri.preingest.loader.unit.PartitionTypes
import akka.actor.ActorRef
import org.freedesktop.dbus._
import scala.collection.mutable
import org.freedesktop.{DBus, UDisks}
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DiskProperties, PartitionProperties, StoreProperties, DeviceFile}

case class DeviceAdded(storeProperties: StoreProperties)
case class DeviceRemoved(deviceFile: DeviceFile)

class UDisksMonitor(udisksMonitor: ActorRef) {

  import org.freedesktop.UDisks.{Device, DeviceRemoved => UDeviceRemoved, DeviceAdded => UDeviceAdded}

  val UDISKS_BUS_NAME = "org.freedesktop.UDisks" //TODO external config
  val UDISKS_PATH = "/org/freedesktop/UDisks" //TODO external config
  val IGNORE_DEVICES = List("^/dev/sda.*".r, "^/dev/dm.*".r, "/dev/sr0".r) //TODO external config

  lazy val dbus = DBusConnection.getConnection(DBusConnection.SYSTEM)

  /* dbusPath -> devicePath */
  val dbusDeviceMappings = mutable.HashMap[String, String]()


  dbus.addSigHandler(classOf[UDeviceAdded], new DBusSigHandler[UDeviceAdded] {
    def handle(event: UDeviceAdded) {
      val path = extractString(event.getWireData()(2))

      val props = getProperties(event.getSource, path, classOf[Device])
      val sProps = getStoreProperties(props)

      dbusDeviceMappings.synchronized {
        dbusDeviceMappings += (path -> sProps.deviceFile) //add mapping
      }

      if(IGNORE_DEVICES.find(_.findFirstMatchIn(sProps.deviceFile).nonEmpty).isEmpty)
        udisksMonitor ! DeviceAdded(sProps)
    }
  })

  dbus.addSigHandler(classOf[UDeviceRemoved], new DBusSigHandler[UDeviceRemoved] {
    def handle(event: UDeviceRemoved) {
      val path = extractString(event.getWireData()(2))
      val devicePath = dbusDeviceMappings(path)

      dbusDeviceMappings.synchronized {
        dbusDeviceMappings -= path //remove mapping
      }

      if(IGNORE_DEVICES.find(_.findFirstMatchIn(devicePath).nonEmpty).isEmpty)
        udisksMonitor ! DeviceRemoved(devicePath)

    }
  })

  def getAttachedDevices() {
    val udisks = dbus.getRemoteObject(UDISKS_BUS_NAME, UDISKS_PATH, classOf[UDisks])

    import scala.collection.JavaConverters._

    dbusDeviceMappings.synchronized {
      udisks.EnumerateDevices().asScala.map {
        device =>
          val path = getRemoteObjectPath(device)
          val props = getProperties(UDISKS_BUS_NAME, path, classOf[Device])
          val sProps = getStoreProperties(props)

          if(IGNORE_DEVICES.find(_.findFirstIn(sProps.deviceFile).nonEmpty).isEmpty) {
            dbusDeviceMappings += (path -> sProps.deviceFile) //add mapping
            udisksMonitor ! DeviceAdded(sProps)
          }
      }
    }
  }

  def close() {
    dbus.disconnect()
  }

  //private def pendingUnit(sp: StoreProperties) = PendingUnit(sp.interface.toUpperCase, sp.deviceFile, sp.getLabel(), Option(sp.size), Option(sp.timestamp * 1000)) //*1000 to get to milliseconds

  private def getStoreProperties(props: Map[String, Variant[_]]) : StoreProperties = {

    //def getInt(key: String) : Int = props(key).getValue.asInstanceOf[UInt32].intValue
    def getLng(key: String) : Long = props(key).getValue.asInstanceOf[UInt64].longValue
    def getStr(key: String) : String = props(key).getValue.asInstanceOf[String]
    def getBool(key: String) : Boolean = props(key).getValue.asInstanceOf[java.lang.Boolean]
    def nonEmpty(s: String) : Option[String] = {
      if( s == null || s.isEmpty)
        None
      else
        Some(s)
    }

    val interface = getStr("DriveConnectionInterface")
    val deviceFile = getStr("DeviceFile")
    val timestamp: Long = getLng("DeviceDetectionTime")

    val isPartition = getBool("DeviceIsPartition")
    //    val isDrive: Boolean = getProp("DeviceIsDrive")
    //    val isOptical: Boolean = getProp("DeviceIsOpticalDisc")
    //    val isRemovable: Boolean = getProp("DeviceIsRemovable")

    if(isPartition) {
      PartitionProperties(
        partitionType = Integer.parseInt(getStr("PartitionType").substring(2), 16),
        partitionLabel = nonEmpty(getStr("PartitionLabel")).orElse(nonEmpty(getStr("IdLabel"))),
        interface,
        deviceFile,
        size = getLng("PartitionSize"),
        timestamp)
    } else {
      DiskProperties(
        getStr("DriveVendor"),
        getStr("DriveModel"),
        getStr("DriveSerial"),
        interface,
        deviceFile,
        size = getLng("DeviceSize"),
        timestamp)
    }
  }


  private def getProperties(busname: String, path: String, iface: Class[_ <: DBusInterface]) = {
    val remoteObject = dbus.getRemoteObject(busname, path, classOf[DBus.Properties])
    val ifaceName = iface.getName.replace("$", ".")

    import scala.collection.JavaConversions._

    remoteObject.GetAll(ifaceName).toMap
  }

  private def getRemoteObjectPath(di: DBusInterface) = {
    val invocationHandler = java.lang.reflect.Proxy.getInvocationHandler(di)
    val fRemote = invocationHandler.getClass.getDeclaredField("remote")
    fRemote.setAccessible(true)
    val remote = fRemote.get(invocationHandler)
    val mGetObjectPath = remote.getClass.getMethod("getObjectPath", Array.empty[Class[_]]: _*)
    mGetObjectPath.setAccessible(true)
    mGetObjectPath.invoke(remote).asInstanceOf[String]
  }

  private def extractString(buf: Array[Byte]): String = {
    val length = Message.demarshallintLittle(buf, 0, 4).asInstanceOf[Int]
    new String(buf, 4, length)
  }
}

object UDisksMonitor {
  val UNKNOWN_LABEL = "<<UNKNOWN>>"

  type DeviceFile = String

  trait StoreProperties {
    def interface: String
    def deviceFile: DeviceFile
    def size: Long
    def timestamp: Long

    def getLabel() : String
  }

  case class DiskProperties(vendor: String, model: String, serial: String, interface: String, deviceFile: String, size: Long, timestamp: Long) extends StoreProperties {
    def getLabel() = s"$vendor $model (S/N: $serial)"
  }

  case class PartitionProperties(partitionType: Int, partitionLabel: Option[String], interface: String, deviceFile: String, size: Long, timestamp: Long) extends StoreProperties {
    def getLabel() = s"${partitionLabel.getOrElse(UDisksMonitor.UNKNOWN_LABEL)} (${PartitionTypes.TYPES(partitionType)})"
  }
}
