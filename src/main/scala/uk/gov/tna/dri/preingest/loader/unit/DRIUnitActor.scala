package uk.gov.tna.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.{ActorRef, Actor}
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._
import uk.gov.tna.dri.preingest.loader.certificate.{CertificateDetail, GetCertificate, CertificateData}

case class Load(username: String, parts: Seq[TargetedPart], certificate: Option[String], passphrase: Option[String])

trait DRIUnitActor[T <: DRIUnit] extends Actor with Logging {

  lazy val certificateManagerActor = context.actorFor("dri-preingest-loader/certificateManagerActor")

  def uid: DRIUnit.UnitUID

    def receive = {
      case SendUnitStatus(listener: ActorRef, clientId: Option[String]) =>
        listener ! UnitStatus(unit, None, clientId)

      case l: Load =>
        l.certificate match {
          case Some(cert) =>
            certificateManagerActor ! GetCertificate(l.username, cert, Option(WithCert(l, _)))
          case None =>
            copyData(l.username, l.parts, l.passphrase)
        }

      case WithCert(Load(username, parts, _, passphrase), certificate) =>
        copyData(username, parts, certificate, passphrase)
    }

    def unit : T

    //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests
    //whilst a copy is happening!
    def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String])
    def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String])
}

case class WithCert(reply: Any, certificate: CertificateDetail)

trait EncryptedDRIUnitSupport {

}
