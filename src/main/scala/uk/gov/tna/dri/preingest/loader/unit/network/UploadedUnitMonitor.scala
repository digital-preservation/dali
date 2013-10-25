package uk.gov.tna.dri.preingest.loader.unit

import akka.actor.{Props, Actor}
import scalax.file.Path
import grizzled.slf4j.Logging
import java.security.{MessageDigest, DigestInputStream}
import scala.collection.mutable.ArrayBuffer
import org.bouncycastle.util.encoders.Hex
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit.UnitUID

//received events
case object ScheduledExecution

//sent events
case class PendingUploadedUnits(pending: List[PendingUnit])

object UploadedUnitMonitor {
  val NETWORK_INTERFACE = "Network"
  val UPLOAD_LOCATION = "/dri-upload" //TODO replace with config
}

/**
 * UnitUID for an Uploaded file is a SHA-1 checksum of the file
 */
class UploadedUnitMonitor extends Actor with Logging {

  //state
  var known = Map[Path, UnitUID]()

  def receive = {

//    case ListPendingUnits =>
//       if(known.isEmpty) {
//         this.known = findPendingUnits(Path.fromString(uploadLocation))
//       }
//       sender ! PendingUploadedUnits(this.known)

    case ScheduledExecution =>
      val foundUnits = findUnits(Path.fromString(UploadedUnitMonitor.UPLOAD_LOCATION))

      //additions
      for(foundUnit <- foundUnits) {
        if(!known.contains(foundUnit)) {

          val uid = unitUid(foundUnit)
          this.known = known + (foundUnit -> uid)

          val unitActor = context.actorOf(Props(new UploadedUnitActor(uid, foundUnit)))
          context.parent ! RegisterUnit(uid, unitActor)

          //nonProcessingUploadedUnits.toList.map(uploadedUnit => PendingUnit(UploadedUnitMonitor.NETWORK_INTERFACE, uploadLocation, uploadedUnit.name, Option(uploadedUnit.size.get), Option(path.lastModified)))
          //context.parent ! Register(pendingUnit)
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
       val md = MessageDigest.getInstance("SHA1")
       val buf = new Array[Byte](4096) //4KB
       var read = 0;
       while(read > -1) {
         read = is.read(buf)
         if(read > -1) {
           md.update(buf)
         }
       }

       Hex.toHexString(md.digest())
    }
  }

  private def findUnits(path: Path): List[Path] = {
    if(path.isDirectory) {
      val uploadedUnits = path * ("*.gpgz") //TODO make configurable
      val processingUploadedUnits = path * ("*.gpgz.processing")

      //filter out the ones we are already processing
      uploadedUnits.filter(uu => processingUploadedUnits.find(_.startsWith(uu)).isEmpty).toList

    } else {
      warn(s"Uploaded Unit Monitor directory: ${path.path} does not exist. No uploaded units will be found!")
      List.empty[Path]
    }
  }
}
