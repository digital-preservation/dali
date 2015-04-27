/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import scalax.file.Path
import scala.collection.mutable
import uk.gov.nationalarchives.dri.preingest.loader.store.DataStore
import scalax.file.PathMatcher.IsDirectory
import uk.gov.nationalarchives.dri.preingest.loader.{certificate, SettingsImpl}
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

  // return the UUID from a luks-encrypted drive
  private def getUUID(settings: SettingsImpl, device: String): UUID = {
    import scala.sys.process._

    // TODO remove sudo
    val UUIDcmd = Seq(
      "sudo",
      settings.Luks.bin.path,
      "luksUUID",
      device
    )
    UUID.fromString(UUIDcmd.!!.trim)
  }

  private def mount(settings: SettingsImpl, device: String, certificate: Option[Path], passphrase: String, mountPoint: Option[String]) {
    import scala.sys.process._

    // withVolumeNoFS does not actually mount, just runs luksOpen to create a mapping point
    // withVolume both maps and mounts; the mountpoint is passed in as a parameter

    val myUUID = getUUID(settings, device)

    val mapCmd = certificate match {
      // if there's a certificate, decrypt it and use it to create a LUKS mapped drive
      // TODO can pipe output from gpg direct to cryptsetup on the command line like so:
      // /usr/bin/gpg --batch --passphrase=testing2 --decrypt /home/dev/Keys/keyfile_246_20141014.gpg  | sudo /sbin/cryptsetup --readonly --key-file=- luksOpen /dev/sdb1 luks-6705d910-4fe0-4562-bf09-fad6452882cb
      // so should be able to replicate this in scala, but gpg objects to the pipe symbol (#|)
      case Some(cert) =>
         // TODO remove sudos
         val tempKeyFile = "/tmp/" + myUUID.toString + ".key"
         val keyFile = Seq(
            settings.Gpg.bin.path,
            "--batch",
            s"--output=$tempKeyFile",
            s"--passphrase=$passphrase",
           "--decrypt",
            cert.path
         )
         keyFile.!
         Seq(
            "sudo",
            settings.Luks.bin.path,
            "--readonly",
            s"--key-file=$tempKeyFile",
            "luksOpen",
            device,
            "luks-" + myUUID
         )
      // otherwise, just use a password
      case None =>
        // TODO remove sudo
        // TODO this branch never tested, would probably fail on pipe
         Seq(
           "echo",
           passphrase,
           "#|",
           "sudo",
           settings.Luks.bin.path,
           "--readonly",
           "--key-file=-",
           "luksOpen",
           device,
           "/luks-" + myUUID
         )
    }
    var resultCode = (mapCmd.!)

    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $mapCmd")
    }  else {
      mountPoint match {
        // don't mount unless we have a mountPoint!
        case Some(mountPathStr) => {
          val mountPath = Path.fromString(mountPathStr)
          // check if mountdir exists and create if it doesn't
          if (! mountPath.isDirectory) {
            // TODO remove sudo
            val mkCmd = "sudo mkdir " + mountPathStr
            resultCode = (mkCmd.!)
            if(resultCode != 0) {
              error(s"Error code '$resultCode' when executing: $mkCmd")
            }
          }
          // TODO just assuming OK - can we use isDirectory again on the same Path?
          // TODO remove sudo
          val mountCmd =  Seq(
            "sudo",
            "mount",
            "-o",
            "ro",  // read only
            settings.Luks.mapPoint.path.toString + "/luks-" + myUUID,
            mountPathStr
          )
          // eg sudo mount /dev/mapper/luks-6705d910-4fe0-4562-bf09-fad6452882cb /home/dev/.dri-upload/IoDMToZhUEosoOHd9mkubzElvVFMI0uqWj1HcZmTbIeWn+rFaw4dEA==/sdb1
          resultCode = (mountCmd.!)

          if(resultCode != 0) {
            error(s"Error code '$resultCode' when executing: $mountCmd")
          }
        }
        case None =>

      }
    }
  }


  private def dismount(settings: SettingsImpl, device: String, mountPoint: Option[Path]) {
    import scala.sys.process._

    val myUUID = getUUID(settings, device)

    // dismount and remove mountpoint directory if required (continue even if fails)
    mountPoint match {
      case Some(point) => {
        // TODO remove sudo
        val dismountCmd = Seq(
          "sudo",
          "umount",
          point.path
        )
        var resultCode = dismountCmd.!
        if(resultCode != 0) {
          error(s"Error code '$resultCode' when executing: $dismountCmd")
        }
        // TODO remove sudo
        val rmCmd = "sudo rmdir " + point.path
        resultCode = rmCmd.!
        if(resultCode != 0) {
          warn(s"Error code '$resultCode' when executing: $rmCmd")
        }
      }
      case None =>
    }
    // always close the mapping
    val closeCmd = Seq(
      // TODO remove sudo
      "sudo",
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

    // TODO remove sudo
    val listCmd = "sudo lsblk -l -n"
    val mountedMaps = new mutable.ListBuffer[MountedMap]

    val listLogger = new ProcessLogger {
      //eg. luks-6705d910-4fe0-4562-bf09-fad6452882cb (dm-5) 253:5    0   1.8T  0 crypt /media/246
      val LsblkItemExtractor = """^([^\s]+)(\s+\(dm\-\d+\))?\s+(\d+)\:(\d+)\s+\d+\s+[^\s]+\s+\d\s+\w+\s+(.*)$""".r

      def out(s: => String) = {
        val o : String = s
        o match {
          case LsblkItemExtractor(deviceName, null, major, minor, mountPoint) =>
            val mm = MountedMap("/dev/" + deviceName, MajorMinor(major.toInt, minor.toInt), mountPoint match {
              case "" =>
                None
              case _ =>
                Option(mountPoint)
            })
            mountedMaps += mm
          case _ =>
        }
      }
      def err(s: => String) = error(s)
      def buffer[T](f: => T) = f
    }

    var resultCode = listCmd ! listLogger

    // got everything but the physical device name, now to find that
    val depsCmd = Seq(
      // TODO remove sudo
      "sudo",
      "dmsetup",
      "deps"
    )
    val mountedVolumes = new mutable.ListBuffer[MountedVolume]
    val depsLogger = new ProcessLogger {
      // eg. luks-encryptedDrive: 1 dependencies : (8, 2)
      // The convention that drive names start with 'luks-' avoids having to make yet
      // another call to exec blkid, which gives a type of 'crypto_LUKS' for the physical device
      val DependencyExtractor = """^(luks\-[^\:]+)\:[^\:]+\:\s+\(([\d]+)\,\s*([\d]+)\)""".r
      // find items in MountedMaps with majorMinor matching dependency majorMinor
      def out(s: => String) = {
        val o : String = s
        o match {
          case DependencyExtractor(mapName, major, minor) => {
            mountedMaps.foreach(mm =>
              if (mm.majorMinor == MajorMinor(major.toInt, minor.toInt)) {
                mountedVolumes += MountedVolume(minor.toInt, mm.deviceName, "/dev/mapper/"+mapName, mm.mountPoint )
              }
            )
          }
          case _ =>
        }
      }
      def err(s: => String) = error(s)
      def buffer[T](f: => T) = f
    }

    resultCode = depsCmd ! depsLogger

    mountedVolumes.toList match {
      case Nil =>
        None
      case list =>
        Option(list.toSeq)
    }

  }

  case class MountedVolume(slot: Int, volume: String, virtualDevice: String, mountPoint: Option[String])
  case class MajorMinor(major: Int, minor: Int)
  case class MountedMap(deviceName: String, majorMinor: MajorMinor,  mountPoint: Option[String])
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
      import uk.gov.nationalarchives.dri.preingest.loader.unit.common.unit.isJunkFile
      val files = mount * ((p: Path) => !isJunkFile(settings, p.name))
      f(files.toSet.toSeq)
    }
  }
}
