package uk.gov.tna.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import akka.actor.Actor
import uk.gov.tna.dri.preingest.loader.unit.{Part, DecryptUnit, DRIUnit}
import uk.gov.tna.dri.preingest.loader.store.DataStore

class PhysicalUnitActor extends Actor with Logging {

  def receive = {

    //TODO subclass of DRI which represents encrypted unit

    case du: DecryptUnit =>

    //TODO this should not be in here!
//    case du: DecryptUnit =>
//      getDecryptedUnitDetails(du).map {
//      case upPendingUnit =>
//        //update state
//        val existingPendingUnit = this.known.find(_.src == du.pendingUnit.src).get
//        val updatedPendingUnit = upPendingUnit.copy(timestamp = existingPendingUnit.timestamp, size = existingPendingUnit.size) //preserve timestamp and size
//        this.known = this.known.updated(this.known.indexWhere(_.src == updatedPendingUnit.src), updatedPendingUnit)
//
//        //update sender
//        sender ! DeRegister(existingPendingUnit)
//        sender ! Register(updatedPendingUnit)
//    }
//  }
//
//  def getDecryptedUnitDetails(du: DecryptUnit) : Option[DRIUnit] = {
//    DataStore.withTemporaryFile(du.certificate) {
//      tmpCert =>
//        TrueCryptedPartition.getVolumeLabel(du.pendingUnit.src, tmpCert, du.passphrase.get).map {
//          volumeLabel =>
//            val updatedPendingUnitLabel = du.pendingUnit.label.replace(DBusUDisks.UNKNOWN_LABEL, volumeLabel)
//            val prts = TrueCryptedPartition.listTopLevelDirs(du.username)(du.pendingUnit.src, tmpCert, du.passphrase.get).map(Part(volumeLabel, _))
//            du.pendingUnit.copy(label = updatedPendingUnitLabel, parts = Some(prts))
//        }
//    }
//  }
  }
}
