/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.unit

import grizzled.slf4j.Logging
import akka.actor.ActorRef
import uk.gov.nationalarchives.dri.preingest.loader.certificate.CertificateDetail
import uk.gov.nationalarchives.dri.preingest.loader._
import uk.gov.nationalarchives.dri.preingest.loader.certificate.NoCertificate
import uk.gov.nationalarchives.dri.preingest.loader.certificate.GetCertificate
import uk.gov.nationalarchives.dri.preingest.loader.catalogue.LoaderCatalogueJmsClient
import uk.gov.nationalarchives.dri.preingest.loader.unit.DRIUnit.UnitUID


case class Load(username: String, parts: Seq[TargetedPart], certificate: Option[String], passphrase: Option[String], clientId: Option[String], unitManager: Option[ActorRef])

/**
 * Base trait of all UnitActors
 */
trait DRIUnitActor[T <: DRIUnit] extends ComposableActor with Logging {

  protected val settings = Settings(context.system)

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
    case Load(username, parts, None, passphrase, clientId, unitManager) => //TODO specific client!
      // test for fixity if requested
      parts.foreach {
         p => if (p.fixity) {
           unitManager match {
            case Some(actor) => actor ! PartFixityProgress(p.part, 0) // trigger display of progress bar
            case None =>
           }
           fixityCheck(username, p, passphrase, unitManager)
         }
      }
      copyData(username, parts, passphrase, unitManager)
  }

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests whilst a copy is happening!
  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], unitManager: Option[ActorRef])
  def fixityCheck(username: String, part: TargetedPart, passphrase: Option[String], unitManager: Option[ActorRef])
}

case class WithCert(reply: Any, certificate: CertificateDetail)
case class UnitError(unit: DRIUnit, error: String)

trait EncryptedDRIUnitActor[T <: EncryptedDRIUnit] extends DRIUnitActor[T] {

  protected lazy val certificateManagerActor = context.actorFor("/user/certificateManagerActor")

  receiveBuilder += {
    case Load(username, parts, Some(certificate), passphrase, clientId, unitManager) => //TODO specific client!
      info("ld EncryptedDRIUnitActor load")
      certificateManagerActor ! GetCertificate(username, certificate, Option(WithCert(Load(username, parts, Option(certificate), passphrase, clientId, unitManager), _)))

    case WithCert(Load(username, parts, _, passphrase, clientId, unitManager), certificate) =>
      info("ld EncryptedDRIUnitActor WithCert load")
      copyData(username, parts, certificate, passphrase, unitManager)

    case NoCertificate(cert, Load(username, _, _, _, _, _)) =>
      error(s"Certificate '$cert' could be retrieved for: $username")
      //TODO let user know of problem

    case udd: UpdateDecryptDetail =>
      info("ld EncryptedDRIUnitActor UpdateDecryptDetail " + udd)
      udd.certificate match {
        case Some(cert) =>
          certificateManagerActor ! GetCertificate(udd.username, cert, Option(WithCert(udd, _)))

        case None =>
          updateDecryptDetail(udd.username, udd.passphrase)
          self ! SendUnitStatus(udd.listener, udd.clientId)
      }

    case WithCert(UpdateDecryptDetail(username, listener, _, passphrase, clientId), certificate) =>
      info("ld EncryptedDRIUnitActor withCert ")
      updateDecryptDetail(username, listener, certificate, passphrase)
      info("sending status to self " + self + "status " + SendUnitStatus(listener, clientId))
      self ! SendUnitStatus(listener, clientId)


    case NoCertificate(cert, UpdateDecryptDetail(_, listener, _, _, clientId)) =>
      //let client know that certificate could not be found!
      listener ! UserProblemNotification(UserErrorMessages.NO_CERTIFICATE("decrypting unit detail", unit.humanId, cert) , clientId)
  }

  //TODO copying should be moved into a different actor, otherwise this actor cannot respond to GetStatus requests whilst a copy is happening!
  def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String], unitManager: Option[ActorRef])
  def updateDecryptDetail(username: String, passphrase: String )
  def updateDecryptDetail(username: String, listener: ActorRef, certificate: CertificateDetail, passphrase: String)
  def fixityCheck(username: String, part: TargetedPart, passphrase: Option[String], unitManager: Option[ActorRef])
}
