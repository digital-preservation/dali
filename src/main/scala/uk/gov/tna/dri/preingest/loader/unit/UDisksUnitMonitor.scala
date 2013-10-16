package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.{ActorRef, Actor}
import org.freedesktop.dbus.{DBusInterface, Message, DBusSigHandler, DBusConnection}
import org.freedesktop.UDisks
import org.freedesktop.UDisks.{DeviceRemoved, DeviceAdded}

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
      this.known = this.known.filterNot(_ == d.pendingUnit)
      context.parent ! d
  }

  override def postStop() {
    udisks.close()
  }
}

class DBusUDisks(udisksUnitMonitor: ActorRef) {

  lazy val dbus = DBusConnection.getConnection(DBusConnection.SYSTEM)

  dbus.addSigHandler(classOf[DeviceAdded], new DBusSigHandler[DeviceAdded] {
    def handle(event: DeviceAdded) {
      val dbusPath = extractString(event.getWireData()(2))
      udisksUnitMonitor ! Register(PendingUnit(dbusPath, dbusPath))
    }
  })

  dbus.addSigHandler(classOf[DeviceRemoved], new DBusSigHandler[DeviceRemoved] {
    def handle(event: DeviceRemoved) {
      val dbusPath = extractString(event.getWireData()(2))
      udisksUnitMonitor ! DeRegister(PendingUnit(dbusPath, dbusPath))
    }
  })

  def getAttachedDevices() : List[PendingUnit] = {
    val udisks = dbus.getRemoteObject("org.freedesktop.UDisks", "/org/freedesktop/UDisks", classOf[UDisks]).asInstanceOf[UDisks]

    import scala.collection.JavaConverters._

    udisks.EnumerateDevices().asScala.map {
      device =>
        val path = getRemoteObjectPath(device)
        PendingUnit(path, path)
    }.toList
  }

  def close() {
    dbus.disconnect()
  }

  private def getRemoteObjectPath(di: DBusInterface) = {
    val invocationHandler = java.lang.reflect.Proxy.getInvocationHandler(di)
    val fRemote = invocationHandler.getClass.getDeclaredField("remote")
    fRemote.setAccessible(true)
    val remote = fRemote.get(invocationHandler)
    //val mGetObjectPath = remote.getClass.getMethod("getObjectPath", null)
    val mGetObjectPath = remote.getClass.getMethod("getObjectPath", Array.empty[Class[_]]: _*)
    mGetObjectPath.setAccessible(true)
    mGetObjectPath.invoke(remote).asInstanceOf[String]
  }

  private def extractString(buf: Array[Byte]): String = {
    val length = Message.demarshallintLittle(buf, 0, 4).asInstanceOf[Int]
    new String(buf, 4, length)
  }
}
