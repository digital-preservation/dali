package uk.gov.tna.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import scalax.file.Path
import scala.collection.mutable
import uk.gov.tna.dri.preingest.loader.store.DataStore
import scalax.file.PathMatcher.IsDirectory
import uk.gov.tna.dri.preingest.loader.{certificate, SettingsImpl}
import java.util.UUID

/**
 * if the exec's in here fail with the error
 * "Enter your user password or administrator password: "
 * then the problem is sudo and you need to set "!requiretty"
 * for the user account that this code is running under as LUKS needs to sudo
 *
 * Much of this code is fighting to get LUKS to fit into the truecrypt pattern - left to
 * itself, Gnome would request password and mount a LUKS drive without any code needed
 * The Type (crypto_LUKS) is visible on the device from blkid and could be used directly in UDisksUnitMonitor.
 * An alternative would be to get Scala to exec bash scripts with the command sequences
 */
object LUKS extends Logging {


  def withVolume[T](settings: SettingsImpl, volume: String, certificate: Option[Path], passphrase: String, mountPoint: Path)(volumeOperation: => T) : T = {
    try {
      mount(settings, volume, certificate, passphrase, Some(mountPoint.path))
      volumeOperation
    } finally {
      dismount(settings, volume, Some(mountPoint))
    }
  }

  def withVolumeNoFs[T](settings: SettingsImpl, volume: String, certificate: Option[Path], passphrase: String)(volumeOperation: => T) : T = {
    try {
      mount(settings, volume, certificate, passphrase, None)
      volumeOperation
    } finally {
      dismount(settings, volume, None)
    }
  }

  private def getUUID(settings: SettingsImpl, device: String): UUID = {
    import scala.sys.process._

    val UUIDcmd = Seq(
      settings.Luks.bin.path,
      "luksUUID",
      device
    )
    // TODO errorcheck this
    UUID.fromString(UUIDcmd.!!)
  }

  private def mount(settings: SettingsImpl, device: String, certificate: Option[Path], passphrase: String, mountPoint: Option[String]) {
    import scala.sys.process._

    // withVolumeNoFS does not actually mount, just runs luksOpen to create a mapping point
    // withVolume both maps and mounts; the mountpoint is passed in as a parameter

    val myUUID = getUUID(settings, device)

    val mapCmd = certificate match {
      // if there's a certificate, decrypt it and use it to create a LUKS mapped drive
      case Some(cert) =>
         Seq(
            settings.Gpg.bin.path,
            "--decrypt",
            "--batch",
            "--passphrase",
            passphrase,
            cert.path,
            "2#>/dev/null",
            "#|",
            settings.Luks.bin.path,
            "--readonly",
            "--key-file=-",
            "luksOpen",
            device,
            settings.Luks.mapPoint.path + "/luks-" + myUUID
         )
      // otherwise, just use a password
      case None =>
         Seq(
           "echo",
           passphrase,
           "|",
           settings.Luks.bin.path,
           "--readonly",
           "--key-file=-",
           "luksOpen",
           device,
           settings.Luks.mapPoint.path + "/luks-" + myUUID
         )
    }
    var resultCode = (mapCmd.!)

    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $mapCmd")
    }  else {
      mountPoint match {
        // don't mount unless we have a mountPoint!
        case Some(mountPath) => {
          // check if mountdir exists and create if it doesn't
          if (! scalax.file.Path(mountPath).isDirectory) {
            val mkCmd = "mkdir " + mountPath
            resultCode = (mkCmd.!)
            if(resultCode != 0) {
              error(s"Error code '$resultCode' when executing: $mkCmd")
            }
          }
          // TODO just assuming OK - can we use isDirectory again on the same Path?
          val mountCmd =  Seq(
            "mount",
            settings.Luks.mapPoint.path.toString + "/luks-" + myUUID,
            mountPath
          )
          resultCode = (mountCmd.!)

          if(resultCode != 0) {
            error(s"Error code '$resultCode' when executing: $mountCmd")
          }
        }
        case None =>

      }
    }
  }

  // find drive label from mapped drive (ie must be called after luksOpen has run)
//  private def getLabel(settings: SettingsImpl, device: String): Option[String] = {
//
//    val myUUID = getUUID(settings, device)
//    // find the label for the mapped drive
//    val labelCmd = Seq(
//      "blkid",
//      "/dev/mapper/luks-" + myUUID,
//      "-o",
//      "full"
//    )
//    val LabelExtractor = """^.*LABEL\=\"([^\"]+)\".*$""".r
//
//    labelCmd.!! match {
//      case LabelExtractor(label) => {
//        Some(label)
//      }
//      case _ => {
//        error(s"Error when executing: $labelCmd")
//        None
//      }
//    }
//  }

  private def dismount(settings: SettingsImpl, device: String, mountPoint: Option[Path]) {
    import scala.sys.process._

    val myUUID = getUUID(settings, device)

    // dismount and remove mountpoint directory if required (continue even if fails)
    mountPoint match {
      case Some(point) => {
        val dismountCmd = Seq(
          "umount",
          device
        )
        var resultCode = dismountCmd.!
        if(resultCode != 0) {
          error(s"Error code '$resultCode' when executing: $dismountCmd")
        }
        val rmCmd = "rmdir " + point
        resultCode = rmCmd.!
        if(resultCode != 0) {
          warn(s"Error code '$resultCode' when executing: $rmCmd")
        }
      }
      case None =>
    }
    // always close the mapping
    val closeCmd = Seq(
      settings.Luks.bin.path,
        "luksClose",
        "luks-" + myUUID
    )
    val resultCode = closeCmd.!
    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $closeCmd")
    }
  }


  // the name of this function (which matches the Truecrypt name) is misleading: it finds
  // volumes which have been mapped via luksOpen, but not necessarily mounted in the filesystem
  def listLUKSMountedVolumes(settings: SettingsImpl) : Option[Seq[MountedVolume]] = {
    import scala.sys.process._

    val listCmd = "lsblk -l"
    //eg. luks-6705d910-4fe0-4562-bf09-fad6452882cb (dm-5) 253:5    0   1.8T  0 crypt /media/246
    val LsblkItemExtractor = """^([^\s]+)(\s+\(dm\-\d+\))?\s+(\d+)\:(\d+)\s+\d+\s+[^\s]+\s+\w+\s+(.*)$""".r
    val mountedMaps = new mutable.ListBuffer[MountedMap]
    listCmd.!! match {
      case LsblkItemExtractor(deviceName, null, major, minor, mountPoint) =>
         mountedMaps += MountedMap("/dev/" + deviceName, MajorMinor(major.asInstanceOf[Int], minor.asInstanceOf[Int]), mountPoint)      // mountPoint may be empty
      case _ =>
    }
    // got everything but the physical device name, now to find that
    val depsCmd = Seq(
      "dmsetup",
      "deps"
    )
    // eg. luks-encryptedDrive: 1 dependencies : (8,2)
    // The convention that drive names start with 'luks-' avoids having to make yet
    // another call to exec blkid, which gives a type of 'crypto_LUKS' for the physical device
    val DependencyExtractor = """^luks-([^\:]+)\:[^\:]+\:[^\(]+\(([\d]+)\,([\d]+)""".r
    // find items in MountedMaps with majorMinor matching dependency majorMinor
    val mountedVolumes = new mutable.ListBuffer[MountedVolume]
    depsCmd.!! match {
      case DependencyExtractor(mapName, major, minor) => {
        mountedMaps.foreach(mm =>
          if (mm.majorMinor == MajorMinor(major.asInstanceOf[Int], minor.asInstanceOf[Int])) {
            mountedVolumes += MountedVolume(minor.asInstanceOf[Int], mapName, mm.deviceName, Option(mm.mountPoint) )
          }
        )
      }
      case _ =>
    }


//    val blkCmd = "blkid"
//    // eg. /dev/sdd1: UUID="6705d910-4fe0-4562-bf09-fad6452882cb" TYPE="crypto_LUKS"
//    val BlkidDeviceExtractor = """([^\:]+)\:\s+(LABEL\=\"[^\"]+\"\s+)?UUID\=\"([^\"]+)\"\s+TYPE\=\"([^\"]+\"\s*$""".r
//    val mountedVolumes = new mutable.ListBuffer[MountedVolume]
//    blkCmd.!! match {
//      case BlkidDeviceExtractor(device, null, uuid, format) =>
//        if (format == "crypto_LUKS") {
//          // O(x^2), but x tiny
//          mountedMaps.foreach(mm =>
//            if (mm.uuid == uuid) {
//              mountedVolumes += MountedVolume(mm.slot, "luks-" + uuid, device, Option(mm.mountPoint) )
//            }
//          )
//        }
//      case _ =>
//    }
    mountedVolumes.toList match {
      case Nil =>
        None
      case list =>
        Option(list.toSeq)
    }

  }

  case class MountedVolume(slot: Int, volume: String, virtualDevice: String, mountPoint: Option[String])
  case class MajorMinor(major: Int, minor: Int)
  case class MountedMap(deviceName: String, majorMinor: MajorMinor,  mountPoint: String)
}

object LUKSEncryptedPartition {

  def getVolumeLabel(settings: SettingsImpl, volume: String, certificate: Option[Path], passphrase: String): Option[String] = {
    LUKS.withVolumeNoFs(settings, volume, certificate, passphrase) {
      val luksVirtualDevice = LUKS.listLUKSMountedVolumes(settings).map(_.filter(_.volume == volume).head.virtualDevice)
      luksVirtualDevice.flatMap(NTFS.getLabel(settings, _))
    }
  }

  def listTopLevel[T](settings: SettingsImpl, volume: String, mount: Path, certificate: Option[Path], passphrase: String)(f: Seq[Path] => T): T = {
    LUKS.withVolume(settings: SettingsImpl, volume: String, certificate, passphrase, mount) {
      import uk.gov.tna.dri.preingest.loader.unit.common.unit.isJunkFile
      val files = mount * ((p: Path) => !isJunkFile(settings, p.name))
      f(files.toSet.toSeq)
    }
  }
}
