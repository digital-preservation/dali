package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.{ActorRef, Actor}
import org.freedesktop.dbus._
import org.freedesktop.{DBus, UDisks}
import org.freedesktop.UDisks.{Device, DeviceRemoved, DeviceAdded}
import scala.collection.mutable
import scalax.file.Path
import scala._
import scala.Some
import uk.gov.tna.dri.preingest.loader.store.DataStore
import scalax.file.PathMatcher.IsDirectory

case class PendingAttachedUnits(pending: List[PendingUnit])


class UDisksUnitMonitor extends Actor with Logging {

  lazy val udisks = new DBusUDisks(this.self)

  //mutable
  var known = List[PendingUnit]()

  def receive = {

    case ListPendingUnits =>
      if(known.isEmpty) {
        this.known = udisks.getAttachedDevices()
      }
      sender ! PendingAttachedUnits(known)

    //received from DBusUDisks on DeviceAdded
    case r: Register =>
      this.known = r.pendingUnit :: this.known
      context.parent ! r

    //received from DBusUDisks on DeviceRemoved
    case d: DeRegister =>
      this.known = this.known.filterNot(_.src == d.pendingUnit.src)
      context.parent ! d


    case du: DecryptUnit =>
      getDecryptedUnitDetails(du).map {
        case upPendingUnit =>
          //update state
          val existingPendingUnit = this.known.find(_.src == du.pendingUnit.src).get
          val updatedPendingUnit = upPendingUnit.copy(timestamp = existingPendingUnit.timestamp, size = existingPendingUnit.size) //preserve timestamp and size
          this.known = this.known.updated(this.known.indexWhere(_.src == updatedPendingUnit.src), updatedPendingUnit)

          //update sender
          sender ! DeRegister(existingPendingUnit)
          sender ! Register(updatedPendingUnit)
      }
  }

  def getDecryptedUnitDetails(du: DecryptUnit) : Option[PendingUnit] = {
    withTemporaryFile(du.certificate) {
      tmpCert =>
        TrueCryptedPartition.getVolumeLabel(du.pendingUnit.src, tmpCert, du.passphrase.get).map {
          volumeLabel =>
            val updatedPendingUnitLabel = du.pendingUnit.label.replace(DBusUDisks.UNKNOWN_LABEL, volumeLabel)
            val prts = TrueCryptedPartition.listTopLevelDirs(du.username)(du.pendingUnit.src, tmpCert, du.passphrase.get).map(Part(volumeLabel, _))
            du.pendingUnit.copy(label = updatedPendingUnitLabel, parts = Some(prts))
        }
    }
  }

  def withTemporaryFile[T](fileDetail: Option[(String, Array[Byte])])(f: Option[Path] => T) : T = fileDetail match {
    case Some((name, data)) =>
      val tmpFile = Path.createTempFile(deleteOnExit = true)
      try {
        tmpFile.write(data)
        f(Option(tmpFile))
      } finally {
        tmpFile.delete(force = true)
      }
    case None =>
      f(None)
  }

  override def postStop() {
    udisks.close()
  }
}

object TrueCryptedPartition {

  def getVolumeLabel(volume: String, certificate: Option[Path], passphrase: String) : Option[String] =
    TrueCrypt.withVolumeNoFs(volume, certificate, passphrase) {
      val tcVirtualDevice = TrueCrypt.listTruecryptMountedVolumes.map(_.filter(_.volume == volume).head.virtualDevice)
      tcVirtualDevice.flatMap(NTFS.getLabel)
    }

  def listTopLevelDirs(username: String)(volume: String, certificate: Option[Path], passphrase: String) : Seq[String] = {
    val mountPoint = DataStore.userStore(username) / s"tc_${volume.split('/').last}"
    if(!mountPoint.exists) {
      mountPoint.createDirectory()
    }

    TrueCrypt.withVolume(volume, certificate, passphrase, mountPoint) {
      val subDirs = mountPoint * IsDirectory filterNot { dir => isWindowsJunkDir(dir.name) }
      subDirs.toSeq.map(_.name)
    }
  }

  private def isWindowsJunkDir(name: String) = name.matches("System Volume Information|^Recycler.*|^\\..+")

}

object NTFS extends Logging {

  val NTFSLABEL_CMD = "/sbin/ntfslabel"

  def getLabel(volume: String) : Option[String] = {
    import scala.sys.process._

    val labelCmd = Seq("sudo", NTFSLABEL_CMD, volume)

    val labelLogger = new ProcessLogger {
      val stdOut = new mutable.ListBuffer[String]
      def out(s: => String) = stdOut += s
      def err(s: => String) = error(s)
      def buffer[T](f: => T) = f
    }

    val resultCode = labelCmd ! labelLogger
    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $labelCmd")
    }

    val label = labelLogger.stdOut.reduceLeft(_.trim + _.trim)
    if(label.isEmpty) {
      None
    } else {
      Option(label)
    }
  }
}

/**
 * if the exec's in here fail with the error
 * "Enter your user password or administrator password: "
 * then the problem is sudo and you need to set "!requiretty"
 * for the user account that this code is running under as TrueCrypt needs to sudo
 */
object TrueCrypt extends Logging {

  val TRUECRYPT_CMD = "/usr/bin/truecrypt" //TODO make configurable

  def withVolume[T](volume: String, certificate: Option[Path], passphrase: String, mountPoint: Path)(volumeOperation: => T) : T = {
    try {
      mount(volume, certificate, passphrase, Seq(mountPoint.path))
      volumeOperation
    } finally {
      dismount(volume)
    }
  }

  def withVolumeNoFs[T](volume: String, certificate: Option[Path], passphrase: String)(volumeOperation: => T) : T = {
    try {
      mount(volume, certificate, passphrase, Seq("--filesystem=none"))
      volumeOperation
    } finally {
      dismount(volume)
    }
  }

  private def mount(device: String, certificate: Option[Path], passphrase: String, extraCmdOptions: Seq[String]) {
    import scala.sys.process._

    val mountCmd = Seq(
      TRUECRYPT_CMD,
      "--text",
      "--protect-hidden=no",
      "--fs-options=ro,uid=dev,gid=dev",
      "--mount-options=ro",
      s"--password=$passphrase",         //TODO should not pass as an arg, should pass on StdIn!
      "--mount", device) ++ (certificate match {
      case Some(certificate) =>
        Seq(
          s"--keyfiles=${certificate.path}"
        )
      case None =>
        Nil
    }) ++ extraCmdOptions

    //val resultCode = (mountCmd #< (s"$passphrase")).!
    val resultCode = mountCmd.!


    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $mountCmd")
    }
  }

  private def dismount(device: String) {
    import scala.sys.process._

    val dismountCmd = Seq(
      TRUECRYPT_CMD,
      "--text",
      "--dismount",
      device)

    val resultCode = dismountCmd.!
    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $dismountCmd")
    }
  }

  def listTruecryptMountedVolumes : Option[Seq[MountedVolume]] = {
    import scala.sys.process._
    val listCmd = Seq(TRUECRYPT_CMD, "--text", "--list")

    //extracts mounted volumes from the list produced by tryecrypt list command
    val listLogger = new ProcessLogger {
      val TCListItemExtractor = """([0-9]+):\s([a-z0-9_\-/]+)\s([a-z0-9_\-/]+)\s([A-Za-z0-9_\-/]+)\s""".r

      val mountedVolumes = new mutable.ListBuffer[MountedVolume]

      def out(s: => String) = {
        val o : String = s
        o match {
          case TCListItemExtractor(slot, volume, virtualDevice, mountPoint) =>
            val mv = MountedVolume(slot.toInt, volume, virtualDevice, mountPoint match {
              case "-" =>
                None
              case _ =>
                Option(mountPoint)
            })
            mountedVolumes += mv

          case _ =>
        }
      }
      def err(s: => String) = error(s)
      def buffer[T](f: => T) = f
    }

    val resultCode = listCmd ! listLogger
    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $listCmd")
    }

    listLogger.mountedVolumes.toList match {
      case Nil =>
        None
      case list =>
        Option(list.toSeq)
    }
  }

  case class MountedVolume(slot: Int, volume: String, virtualDevice: String, mountPoint: Option[String])
}

object DBusUDisks {
  val UNKNOWN_LABEL = "<<UNKNOWN>>"
}

class DBusUDisks(udisksUnitMonitor: ActorRef) {

  val UDISKS_BUS_NAME = "org.freedesktop.UDisks" //TODO external config
  val UDISKS_PATH = "/org/freedesktop/UDisks" //TODO external config
  val IGNORE_DEVICES = List("^/dev/sda.*".r, "^/dev/dm.*".r, "/dev/sr0".r) //TODO external config

  lazy val dbus = DBusConnection.getConnection(DBusConnection.SYSTEM)

  /* dbusPath -> devicePath */
  val dbusDeviceMappings = mutable.HashMap[String, String]()


  dbus.addSigHandler(classOf[DeviceAdded], new DBusSigHandler[DeviceAdded] {
    def handle(event: DeviceAdded) {
      val path = extractString(event.getWireData()(2))

      val props = getProperties(event.getSource, path, classOf[Device])
      val sProps = getStoreProperties(props)

      dbusDeviceMappings.synchronized {
        dbusDeviceMappings += (path -> sProps.deviceFile) //add mapping
      }

      if(IGNORE_DEVICES.find(_.findFirstMatchIn(sProps.deviceFile).nonEmpty).isEmpty)
        udisksUnitMonitor ! Register(pendingUnit(sProps))
    }
  })

  dbus.addSigHandler(classOf[DeviceRemoved], new DBusSigHandler[DeviceRemoved] {
    def handle(event: DeviceRemoved) {
      val path = extractString(event.getWireData()(2))
      val devicePath = dbusDeviceMappings(path)

      dbusDeviceMappings.synchronized {
        dbusDeviceMappings -= path //remove mapping
      }

      if(IGNORE_DEVICES.find(_.findFirstMatchIn(devicePath).nonEmpty).isEmpty)
        udisksUnitMonitor ! DeRegister(PendingUnit("Unknown", devicePath, devicePath, None, None))

    }
  })

  def getAttachedDevices() : List[PendingUnit] = {
    val udisks = dbus.getRemoteObject(UDISKS_BUS_NAME, UDISKS_PATH, classOf[UDisks])

    import scala.collection.JavaConverters._

    dbusDeviceMappings.synchronized {
      udisks.EnumerateDevices().asScala.map {
        device =>
          val path = getRemoteObjectPath(device)
          val props = getProperties(UDISKS_BUS_NAME, path, classOf[Device])
          val sProps = getStoreProperties(props)

          dbusDeviceMappings += (path -> sProps.deviceFile) //add mapping

          sProps

      }.filterNot(sp => IGNORE_DEVICES.find(_.findFirstIn(sp.deviceFile).nonEmpty).nonEmpty).map(pendingUnit(_)).toList //filter pendingUnits for IGNORE_DEVICES
    }
  }

  def close() {
    dbus.disconnect()
  }

  private def pendingUnit(sp: StoreProperties) = PendingUnit(sp.interface.toUpperCase, sp.deviceFile, sp.getLabel(), Option(sp.size), Option(sp.timestamp * 1000)) //*1000 to get to milliseconds

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

  abstract class StoreProperties(val interface: String, val deviceFile: String, val size: Long, val timestamp: Long) {
    def getLabel() : String
  }

  case class DiskProperties(vendor: String, model: String, serial: String, override val interface: String, override val deviceFile: String, override val size: Long, override val timestamp: Long) extends StoreProperties(interface, deviceFile, size, timestamp) {
    def getLabel() = s"$vendor $model (S/N: $serial)"
  }

  case class PartitionProperties(partitionType: Int, partitionLabel: Option[String], override val interface: String, override val deviceFile: String, override val size: Long, override val timestamp: Long) extends StoreProperties(interface, deviceFile, size, timestamp){
    def getLabel() = s"${partitionLabel.getOrElse(DBusUDisks.UNKNOWN_LABEL)} (${PartitionTypes.TYPES(partitionType)})"
  }
}
