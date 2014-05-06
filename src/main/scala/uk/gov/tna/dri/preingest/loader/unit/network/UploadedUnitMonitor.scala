package uk.gov.tna.dri.preingest.loader.unit

import akka.actor.{Props, Actor}
import scalax.file.Path
import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID
import uk.gov.tna.dri.preingest.loader.{Settings, Crypto}
import uk.gov.tna.dri.preingest.loader.Crypto.DigestAlgorithm

//received events
case object ScheduledExecution

//sent events
case class PendingUploadedUnits(pending: List[PendingUnit])

/**
 * UnitUID for an Uploaded file is a digest of the file
 */
class UploadedUnitMonitor extends Actor with Logging {

  private val settings = Settings(context.system)

  //state
  var known = Map[Path, UnitUID]()

  def receive = {

    case ScheduledExecution =>
      val foundUnits = findUnits(settings.Unit.uploadedSource)

      //additions
      for(foundUnit <- foundUnits) {
        if(!known.contains(foundUnit)) {

          val uid = unitUid(foundUnit)
          this.known = known + (foundUnit -> uid)

          val unitActor = context.actorOf(Props(new UploadedUnitActor(uid, foundUnit)))
          context.parent ! RegisterUnit(uid, unitActor)
        }
      }
    
      //subtractions
      for(knownUnit <- this.known.keys) {
        if(!foundUnits.contains(knownUnit)) {
            this.known = known.filterNot(kv => kv._1 == knownUnit)
            context.parent ! DeRegisterUnit(this.known(knownUnit))
        }
      }
  }

  private def unitUid(unitPath: Path) = {
    //create checksum of unit
    unitPath.inputStream().acquireAndGet {
     is =>
       Crypto.hexUnsafe(Crypto.digest(is, settings.Unit.uploadedUidGenDigestAlgorithm))
    }
  }

  private def findUnits(path: Path): List[Path] = {
    if(path.isDirectory) {
      val uploadedUnits = path * (s"*.${settings.Unit.uploadedGpgZipFileExtension}")
      val processingUploadedUnits = path * (s"*.${settings.Unit.uploadedGpgZipFileExtension}.processing")

      //filter out the ones we are already processing
      uploadedUnits.filter(uu => processingUploadedUnits.find(_.startsWith(uu)).isEmpty).toList

    } else {
      warn(s"Uploaded Unit Monitor directory: ${path.path} does not exist. No uploaded units will be found!")
      List.empty[Path]
    }
  }
}
