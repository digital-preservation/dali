package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.ActorRef
import uk.gov.tna.dri.preingest.loader.certificate.{NoCertificate, CertificateDetail, GetCertificate}
import uk.gov.tna.dri.preingest.loader.{PreIngestLoaderActor, PreIngestLoader, ComposableActor, UserErrorMessages}


case class Load(username: String, parts: Seq[TargetedPart], certificate: Option[String], passphrase: Option[String], clientId: Option[String], clientSender: Option[ActorRef])

/**
 * Base trait of all UnitActors
 */
trait DRIUnitActor[T <: DRIUnit] extends ComposableActor with Logging {

  /**
   * Unit itself
   */
  var unit : T

  //handle messages
  receiveBuilder += {

    case SendUnitStatus(listener: ActorRef, clientId: Option[String]) =>
      listener ! UnitStatus(unit, None, clientId)

    //below case statement is for non certificate-encrypted units, certificate encrypted units
    //are handled in EncryptedDRIUnitActor
    case Load(username, parts, None, passphrase, clientId, clientSender) => //TODO specific client!
      copyData(username, parts, passphrase, clientSender)
  }

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests whilst a copy is happening!
  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], clientSender: Option[ActorRef])
}

case class WithCert(reply: Any, certificate: CertificateDetail)
case class UnitError(error: String)

trait EncryptedDRIUnitActor[T <: EncryptedDRIUnit] extends DRIUnitActor[T] {

  protected lazy val certificateManagerActor = context.actorFor("/user/certificateManagerActor")

  receiveBuilder += {
    case Load(username, parts, Some(certificate), passphrase, clientId, clientSender) => //TODO specific client!
      certificateManagerActor ! GetCertificate(username, certificate, Option(WithCert(Load(username, parts, Option(certificate), passphrase, clientId, clientSender), _)))

    case WithCert(Load(username, parts, _, passphrase, clientId, clientSender), certificate) =>
      copyData(username, parts, certificate, passphrase, clientSender)

    case NoCertificate(cert, Load(username, _, _, _, _, _)) =>
      error(s"Certificate '$cert' could be retrieved for: $username")
      //TODO let user know of problem

    case udd: UpdateDecryptDetail =>
      udd.certificate match {
        case Some(cert) =>
          certificateManagerActor ! GetCertificate(udd.username, cert, Option(WithCert(udd, _)))

        case None =>
          updateDecryptDetail(udd.username, udd.passphrase)
          self ! SendUnitStatus(udd.listener, udd.clientId)
      }

    case WithCert(UpdateDecryptDetail(username, listener, _, passphrase, clientId), certificate) =>
      updateDecryptDetail(username, certificate, passphrase)
      self ! SendUnitStatus(listener, clientId)

    case NoCertificate(cert, UpdateDecryptDetail(_, listener, _, _, clientId)) =>
      //let client know that certificate could not be found!
      listener ! UserProblemNotification(UserErrorMessages.NO_CERTIFICATE("decrypting unit detail", unit.humanId, cert) , clientId)
  }

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests whilst a copy is happening!
  def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String], clientSender: Option[ActorRef])
  def updateDecryptDetail(username: String, passphrase: String)
  def updateDecryptDetail(username: String, certificate: CertificateDetail, passphrase: String)
}
