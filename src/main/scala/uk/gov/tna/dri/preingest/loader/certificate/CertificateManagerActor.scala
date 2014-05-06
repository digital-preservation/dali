package uk.gov.tna.dri.preingest.loader.certificate

import akka.actor.Actor
import grizzled.slf4j.Logging
import scalax.file.{NotFileException, FileOps, Path}
import java.net.NetworkInterface
import java.io.FileNotFoundException
import uk.gov.tna.dri.preingest.loader.store.DataStore
import uk.gov.tna.dri.preingest.loader.{Settings, Crypto}
import uk.gov.tna.dri.preingest.loader.Crypto.{SymetricAlgorithm, DigestAlgorithm}


case class StoreCertificates(username: String, certificates: Seq[CertificateDetail])
case class CertificateRef(name: String, path: Path)
case class Certificate(detail: CertificateDetail)
case class ListCertificates(username: String)
case class GetCertificate(username: String, name: CertificateName, reply: Option[CertificateDetail => Any] = None)
case class CertificateList(certificates: Seq[CertificateName])
case class NoCertificate(name: CertificateName, cause: Any)
case class CertificateManagerError(error: String)

class CertificateManagerActor extends Actor with Logging {

  private val settings = Settings(context.system)

  def receive = {

    case StoreCertificates(username: String, certificates: Seq[CertificateDetail]) =>
      for(certificate <- certificates) {
        DataStore.userStore(settings, username) match {
          case Left(ioe) =>
            error(s"Could not list certificates for user: $username", ioe)
            sender ! CertificateManagerError("Could not access certificates store")

          case Right(ks) =>
            storeCertificate(ks, certificate, passphrase(username)) match {
              case Left(t) =>
                error(s"Could not store the certificate for user: $username", t)
                sender ! CertificateManagerError("Could not store certificate")
              case Right(certificatePath) =>
                sender ! CertificateRef(certificate._1, certificatePath)
            }
        }
      }

    case ListCertificates(username: String) =>
      DataStore.userStore(settings, username) match {
        case Left(ioe) =>
          error(s"Could not list certificates for user: $username", ioe)
          sender ! CertificateManagerError("Could not access certificates store")

        case Right(ks) =>
          val certificates = ks * s"*.${settings.CertificateManager.encryptedFileExtension}"
          val certNames = certificates.seq.map(_.name.replace(s".${settings.CertificateManager.encryptedFileExtension}", "")).toSeq
          sender ! CertificateList(certNames)
      }

    case GetCertificate(username, name, reply) =>
      DataStore.userStore(settings, username) match {
        case Left(ioe) =>
          error(s"Could not list certificates for user: $username", ioe)
          sender ! CertificateManagerError("Could not access certificates store")

        case Right(ks) =>
          val cert = ks / s"$name.${settings.CertificateManager.encryptedFileExtension}"

          getCertificate(cert, passphrase(username)) match {
            case Left(t) =>
              error(t.getMessage, t)
              sender ! NoCertificate(name, reply)

            case Right(certificateData) =>
              reply match {
                case Some(f) =>
                  sender ! f((name, certificateData))
                case None =>
                  sender ! Certificate((name, certificateData))
              }
          }
      }
  }

  /**
   * Generates a passphrase for a user
   *
   * The passphrase is a base64 encoded digest of the username
   * which uses the MAC addresses of the machine as salt
   *
   * @param username
   */
  private def passphrase(username: String) = {
    import scala.collection.JavaConverters._

    val network = NetworkInterface.getNetworkInterfaces.asScala.filter(_.getName().startsWith("eth")).map(_.getHardwareAddress).reduceLeft(_ ++ _)
    Crypto.base64Unsafe(Crypto.digest(username, Option(network), settings.CertificateManager.digestAlgorithm))
  }

  private def storeCertificate(store: Path, certificate: CertificateDetail, passphrase: String): Either[Throwable, Path] = {
    val encryptedCert = Crypto.encrypt(certificate._2, passphrase, Crypto.SymetricAlgorithm.TWOFISH)
    val certificateFile = store / (certificate._1 + "." + settings.CertificateManager.encryptedFileExtension)

    import scala.util.control.Exception._
    val result:Either[Throwable,Unit] = catching(classOf[NotFileException],
      classOf[FileNotFoundException]) either { certificateFile.asInstanceOf[FileOps].write(encryptedCert) }

    result.fold(l => Left(l), r => Right(certificateFile))
  }

  private def getCertificate(certificateFile: Path, passphrase: String) : Either[Throwable, CertificateData] = {

    import scala.util.control.Exception._
    val result : Either[Throwable, CertificateData] = catching(classOf[NotFileException],
      classOf[FileNotFoundException]) either {
      val encryptedCert = certificateFile.asInstanceOf[FileOps].bytes.toArray
      Crypto.decrypt(encryptedCert, passphrase)
    }
    result
  }
}
