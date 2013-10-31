package uk.gov.tna.dri.preingest.loader.certificate

import akka.actor.Actor
import grizzled.slf4j.Logging
import scalax.file.{NotFileException, FileOps, Path}
import java.net.NetworkInterface
import java.io.FileNotFoundException
import uk.gov.tna.dri.preingest.loader.store.DataStore
import uk.gov.tna.dri.preingest.loader.Crypto
import uk.gov.tna.dri.preingest.loader.Crypto.{SymetricAlgorithm, DigestAlgorithm}


case class StoreCertificates(username: String, certificates: Seq[CertificateDetail])
case class CertificateRef(name: String, path: Path)
case class Certificate(detail: CertificateDetail)
case class ListCertificates(username: String)
case class GetCertificate(username: String, name: CertificateName, reply: Option[CertificateDetail => Any] = None)
case class CertificateList(certificates: Seq[CertificateName])

class CertificateManagerActor extends Actor with Logging {

  private val ENCRYPTED_FILE_EXT = "gpg"

  def receive = {

    case StoreCertificates(username: String, certificates: Seq[CertificateDetail]) =>
      for(certificate <- certificates) {
        storeCertificate(DataStore.userStore(username), certificate, passphrase(username)) match {
          case Left(t) =>
            error(t.getMessage, t)
          case Right(certificatePath) =>
            sender ! CertificateRef(certificate._1, certificatePath)
        }
      }

    case ListCertificates(username: String) =>
      val ks = DataStore.userStore(username)
      val certificates = ks * s"*.$ENCRYPTED_FILE_EXT"
      val certNames = certificates.seq.map(_.name.replace(s".$ENCRYPTED_FILE_EXT", "")).toSeq
      sender ! CertificateList(certNames)

    case GetCertificate(username, name, reply) =>
      val ks = DataStore.userStore(username)
      val cert = ks / s"$name.$ENCRYPTED_FILE_EXT"

      getCertificate(cert, passphrase(username)) match {
        case Left(t) =>
          error(t.getMessage, t)
        case Right(certificateData) =>
          reply match {
            case Some(f) =>
              sender ! f((name, certificateData))
            case None =>
              sender ! Certificate((name, certificateData))
          }
      }
  }

  /**
   * Generates a passphrase for a user
   *
   * The passphrase is a base64 encoded sha256 hash of the username
   * which uses the MAC addresses of the machine as salt
   *
   * @param username
   */
  private def passphrase(username: String) = {
    import scala.collection.JavaConverters._

    val network = NetworkInterface.getNetworkInterfaces.asScala.filter(_.getName().startsWith("eth")).map(_.getHardwareAddress).reduceLeft(_ ++ _)
    Crypto.base64Unsafe(Crypto.digest(username, Option(network), DigestAlgorithm.SHA256))
  }

  private def storeCertificate(store: Path, certificate: CertificateDetail, passphrase: String): Either[Throwable, Path] = {
    val encryptedCert = Crypto.encrypt(certificate._2, passphrase, Crypto.SymetricAlgorithm.TWOFISH)
    val certificateFile = store / (certificate._1 + "." + ENCRYPTED_FILE_EXT)

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
