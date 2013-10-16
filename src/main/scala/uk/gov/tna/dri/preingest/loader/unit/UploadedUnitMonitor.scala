package uk.gov.tna.dri.preingest.loader.unit

import akka.actor.Actor
import scalax.file.Path
import grizzled.slf4j.Logging

//received events
case object ScheduledExecution

//sent events
case class PendingUploadedUnits(pending: List[PendingUnit])

class UploadedUnitMonitor extends Actor with Logging {

  val uploadLocation = "/dri-upload" //TODO replace with config

  //mutable
  var known = List[PendingUnit]()

  def receive = {

    case ListPendingUnits =>
       if(known.isEmpty) {
         this.known = findPendingUnits(Path.fromString(uploadLocation))
       }
       sender ! PendingUploadedUnits(this.known)

    case ScheduledExecution =>
      val nowKnownPendingUnits = findPendingUnits(Path.fromString(uploadLocation)) //TODO is sender correct?
      //additions
      for(pendingUnit <- nowKnownPendingUnits.par) {
        if(!known.contains(pendingUnit)) {
            context.parent ! Register(pendingUnit)
        }
      }
      //subtractions
      for(pendingUnit <- known.par) {
        if(!nowKnownPendingUnits.contains(pendingUnit)) {
            context.parent ! DeRegister(pendingUnit)
        }
      }
      this.known = nowKnownPendingUnits
  }

  def findPendingUnits(path: Path): List[PendingUnit] = {
    if(path.isDirectory) {
      val uploadedUnits = path * ("*.gpgz") //TODO make configurable
      val processingUploadedUnits = path * ("*.gpgz.processing")

      //filter out the ones we are already processing
      val nonProcessingUploadedUnits = uploadedUnits.filter(uu => processingUploadedUnits.find(_.startsWith(uu)).isEmpty)

      nonProcessingUploadedUnits.toList.map(uploadedUnit => PendingUnit(path.name, uploadedUnit.path))
    } else {
      warn(s"Uploaded Unit Monitor directory: ${path.path} does not exist. No uploaded units will be found!")
      List.empty[PendingUnit]
    }
  }
}
