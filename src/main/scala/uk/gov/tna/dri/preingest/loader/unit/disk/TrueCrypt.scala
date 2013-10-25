package uk.gov.tna.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import scalax.file.Path
import scala.collection.mutable
import uk.gov.tna.dri.preingest.loader.store.DataStore
import scalax.file.PathMatcher.IsDirectory

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

object TrueCryptedPartition {

  def getVolumeLabel(volume: String, certificate: Option[Path], passphrase: String) : Option[String] =
    TrueCrypt.withVolumeNoFs(volume, certificate, passphrase) {
      val tcVirtualDevice = TrueCrypt.listTruecryptMountedVolumes.map(_.filter(_.volume == volume).head.virtualDevice)
      tcVirtualDevice.flatMap(NTFS.getLabel)
    }

  def tempMountPoint(username: String, volume: String) : Path = {
    val mountPoint = DataStore.userStore(username) / s"tc_${volume.split('/').last}"
    if(!mountPoint.exists) {
      mountPoint.createDirectory()
    }
    mountPoint
  }

  def listTopLevelDirs(username: String)(volume: String, certificate: Option[Path], passphrase: String) : Seq[String] = {
    val mountPoint = tempMountPoint(username, volume)
    TrueCrypt.withVolume(volume, certificate, passphrase, mountPoint) {
      val subDirs = mountPoint * IsDirectory filterNot { dir => DataStore.isWindowsJunkDir(dir.name) }
      subDirs.seq.map(_.name).toSeq
    }
  }
}
