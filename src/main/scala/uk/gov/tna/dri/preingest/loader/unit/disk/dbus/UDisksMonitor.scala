package uk.gov.tna.dri.preingest.loader.unit.disk.dbus

import akka.actor.ActorRef
import org.freedesktop.dbus._
import scala.collection.mutable
import org.freedesktop.{DBus, UDisks}
import uk.gov.tna.dri.preingest.loader.SettingsImpl
import uk.gov.tna.dri.preingest.loader.unit.disk.dbus.UDisksMonitor.{DiskProperties, PartitionProperties, StoreProperties, DeviceFile}
import grizzled.slf4j.Logging

case class DeviceAdded(storeProperties: StoreProperties)
case class DeviceRemoved(deviceFile: DeviceFile)

class UDisksMonitor(settings: SettingsImpl, udisksMonitor: ActorRef) extends Logging {

  import org.freedesktop.UDisks.{Device, DeviceRemoved => UDeviceRemoved, DeviceAdded => UDeviceAdded}

  lazy val dbus = DBusConnection.getConnection(DBusConnection.SYSTEM)

  /* dbusPath -> devicePath */
  val dbusDeviceMappings = mutable.HashMap[String, String]()


  dbus.addSigHandler(classOf[UDeviceAdded], new DBusSigHandler[UDeviceAdded] {
    def handle(event: UDeviceAdded) {
      val path = extractString(event.getWireData()(2))
      //FIXME hack to allow gvfs-mount or similar to try mounting device before reading properties
      // On my dev machine, 5 seconds is not enough, 30 seems always ok
      Thread.sleep(settings.DBus.udisksMountDelay.toLong)
      val props = getProperties(event.getSource, path, classOf[Device])
      val sProps = getStoreProperties(props)

      dbusDeviceMappings.synchronized {
        dbusDeviceMappings += (path -> sProps.deviceFile) //add mapping
      }

      if(settings.DBus.udisksIgnoreDevices.find(_.findFirstMatchIn(sProps.deviceFile).nonEmpty).isEmpty)
        udisksMonitor ! DeviceAdded(sProps)
    }
  })

  dbus.addSigHandler(classOf[UDeviceRemoved], new DBusSigHandler[UDeviceRemoved] {
    def handle(event: UDeviceRemoved) {
      val path = extractString(event.getWireData()(2))
      dbusDeviceMappings.get(path) match {
        case Some(devicePath) =>
          dbusDeviceMappings.synchronized {
            dbusDeviceMappings -= path //remove mapping
          }
          if(settings.DBus.udisksIgnoreDevices.find(_.findFirstMatchIn(devicePath).nonEmpty).isEmpty)
            udisksMonitor ! DeviceRemoved(devicePath)

        case None =>
      }
    }
  })

  def getAttachedDevices() {
    val udisks = dbus.getRemoteObject(settings.DBus.udisksBusName, settings.DBus.udisksPath, classOf[UDisks])

    import scala.collection.JavaConverters._

    dbusDeviceMappings.synchronized {
      udisks.EnumerateDevices().asScala.map {
        device =>
          val path = getRemoteObjectPath(device)
          val props = getProperties(settings.DBus.udisksBusName, path, classOf[Device])
          (path -> getStoreProperties(props))
      }
      .filterNot(x => settings.DBus.udisksIgnoreDevices.find(_.findFirstIn(x._2.deviceFile).nonEmpty).nonEmpty)
      .sortBy(_._2.deviceFile.length)
      .map {
        case (dbusPath, storeProperties) =>
          dbusDeviceMappings += (dbusPath -> storeProperties.deviceFile) //add mapping
          udisksMonitor ! DeviceAdded(storeProperties) //notify monitor
      }
    }
  }

  def close() {
    dbus.disconnect()
  }

  private def getStoreProperties(props: Map[String, Variant[_]]) : StoreProperties = {

    import java.util.{Vector => JVector}
    import scala.collection.JavaConverters._

    //def getInt(key: String) : Int = props(key).getValue.asInstanceOf[UInt32].intValue
    def getLng(key: String) : Long = props(key).getValue.asInstanceOf[UInt64].longValue
    def getStr(key: String) : String = props(key).getValue.asInstanceOf[String]
    def getLst[T](key: String) : List[T] = props(key).getValue.asInstanceOf[JVector[T]].asScala.toList
    def getBool(key: String) : Boolean = props(key).getValue.asInstanceOf[java.lang.Boolean]
    def nonEmpty(s: String) : Option[String] = {
      if( s == null || s.isEmpty)
        None
      else
        Some(s)
    }

    val interface = getStr("DriveConnectionInterface")
    val nativePath = getStr("NativePath")
    val deviceFile = getStr("DeviceFile")
    val timestamp: Long = getLng("DeviceDetectionTime")

    val isPartition = getBool("DeviceIsPartition")
    //    val isDrive: Boolean = getProp("DeviceIsDrive")
    //    val isOptical: Boolean = getProp("DeviceIsOpticalDisc")
    //    val isRemovable: Boolean = getProp("DeviceIsRemovable")

    if(isPartition) {
      PartitionProperties(
        partitionType = try {
          Integer.parseInt(getStr("PartitionType").substring(2), 16)
        } catch {
          case e: Exception =>
            warn(s"Expected PartitionType to be an integer for $nativePath but received ${getStr("PartitionType")}")
            -1
        },
        partitionLabel = nonEmpty(getStr("PartitionLabel")).orElse(nonEmpty(getStr("IdLabel"))),
        interface,
        nativePath,
        deviceFile,
        mounted = if(getBool("DeviceIsMounted")) Option(getLst[String]("DeviceMountPaths")) else None,
        lvmDevice = getStr("IdUsage") == "raid" && getStr("IdType") == "LVM2_member",
        size = getLng("PartitionSize"),
        timestamp)
    } else {
      DiskProperties(
        getStr("DriveVendor"),
        getStr("DriveModel"),
        getStr("DriveSerial"),
        interface,
        nativePath,
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

  type NativePath = String
  type DeviceFile = String
  type Bytes = Long
  type Seconds = Long

  trait StoreProperties {
    def interface: String
    def nativePath: NativePath
    def deviceFile: DeviceFile
    def size: Bytes
    def timestamp: Seconds
  }

  case class DiskProperties(vendor: String, model: String, serial: String, interface: String, nativePath: String, deviceFile: String, size: Bytes, timestamp: Seconds) extends StoreProperties
  case class PartitionProperties(partitionType: Int, partitionLabel: Option[String], interface: String, nativePath: String, deviceFile: String,  mounted: Option[List[String]], lvmDevice: Boolean, size: Bytes, timestamp: Seconds) extends StoreProperties
}
