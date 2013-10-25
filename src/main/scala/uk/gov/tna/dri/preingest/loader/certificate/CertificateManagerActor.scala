package uk.gov.tna.dri.preingest.loader.certificate

import akka.actor.Actor
import grizzled.slf4j.Logging
import scalax.file.{NotFileException, FileOps, Path}
import java.net.NetworkInterface
import java.security.{Security, MessageDigest}
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.openpgp.examples.ByteArrayHandler
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import java.io.FileNotFoundException
import uk.gov.tna.dri.preingest.loader.store.DataStore


case class StoreCertificates(username: String, certificates: Seq[CertificateDetail])
case class CertificateRef(name: String, path: Path)
case class Certificate(detail: CertificateDetail)
case class ListCertificates(username: String)
case class GetCertificate(username: String, name: CertificateName)
case class CertificateList(certificates: Seq[CertificateName])

class CertificateManagerActor extends Actor with Logging {

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  private val SYMETRIC_ALG = SymmetricKeyAlgorithmTags.TWOFISH
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

    case GetCertificate(username, name) =>
      val ks = DataStore.userStore(username)
      val cert = ks / s"$name.$ENCRYPTED_FILE_EXT"

      getCertificate(cert, passphrase(username)) match {
        case Left(t) =>
          error(t.getMessage, t)
        case Right(certificateData) =>
          sender ! Certificate((name, certificateData))
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
    val md = MessageDigest.getInstance("SHA256")
    md.update(network)
    val digest = md.digest(username.getBytes("UTF-8"))

    Base64.toBase64String(digest)
  }

  private def storeCertificate(store: Path, certificate: CertificateDetail, passphrase: String): Either[Throwable, Path] = {
    val encryptedCert = ByteArrayHandler.encrypt(certificate._2, passphrase.toCharArray, certificate._1, SYMETRIC_ALG, false)

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
      ByteArrayHandler.decrypt(encryptedCert, passphrase.toCharArray)
    }
    result
  }
}
