package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.{ActorRef, Actor}
import org.freedesktop.dbus.{Message, DBusSigHandler, DBusConnection}
import org.freedesktop.UDisks
import org.freedesktop.UDisks.{DeviceRemoved, DeviceAdded}

case class PendingAttachedUnits(pending: List[PendingUnit])


class UDisksUnitMonitor extends Actor with Logging {

  lazy val udisks = new DBusUDisks(this.self)

  def receive = {

    case ListPendingUnits =>
      sender ! PendingAttachedUnits(udisks.getAttachedDevices())

    case r: Register =>
      context.parent ! r

    case d: DeRegister =>
      context.parent ! d
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
    for(device <- udisks.EnumerateDevices()) {
      val invocationHandler = java.lang.reflect.Proxy.getInvocationHandler(device)
      val fRemote = invocationHandler.getClass.getDeclaredField("remote")
      fRemote.setAccessible(true)
      val remote = fRemote.get(invocationHandler)
      val mGetObjectPath = remote.getClass.getMethod("getObjectPath", null)
      mGetObjectPath.setAccessible(true)
      val path = mGetObjectPath.invoke(remote).asInstanceOf[String]

      PendingUnit(path, path)
    }
  }

  final def extractString(buf: Array[Byte]): String = {
    val length = Message.demarshallintLittle(buf, 0, 4).asInstanceOf[Int]
    new String(buf, 4, length)
  }

}
