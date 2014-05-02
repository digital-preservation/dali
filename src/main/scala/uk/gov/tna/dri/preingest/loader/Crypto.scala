package uk.gov.tna.dri.preingest.loader

import java.security.{NoSuchAlgorithmException, Security, MessageDigest}
import org.bouncycastle.util.encoders.{Hex, Base64}
import java.io.{IOException, InputStream}
import org.bouncycastle.openpgp.examples.ByteArrayHandler
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags

object Crypto {

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  object DigestAlgorithm extends Enumeration {
    type DigestAlgorithm = Value
    val MD5, SHA256, RIPEMD320 = Value
  }

  object SymetricAlgorithm {
    abstract class Algorithm(val symetricKeyAlgorithmTag: Int)
    object TWOFISH extends Algorithm(SymmetricKeyAlgorithmTags.TWOFISH)
  }

  def encrypt(data: Array[Byte], passphrase: String, alg: SymetricAlgorithm.Algorithm, armor: Boolean = false): Array[Byte] = {
    ByteArrayHandler.encrypt(data, passphrase.toCharArray, "dri", alg.symetricKeyAlgorithmTag, false)
  }

  def decrypt(encryptedData: Array[Byte], passphrase: String): Array[Byte] = {
    ByteArrayHandler.decrypt(encryptedData, passphrase.toCharArray)
  }


  import uk.gov.tna.dri.preingest.loader.Crypto.DigestAlgorithm.DigestAlgorithm

  type DigestProvider[E <: Exception] = {def calculate(): Either[E, Array[Byte]]}

  private def digester(alg: DigestAlgorithm): Either[NoSuchAlgorithmException, MessageDigest] = {
    try {
      Right(MessageDigest.getInstance(alg.toString))
    } catch {
      case nsae: NoSuchAlgorithmException =>
        Left(nsae)
    }
  }

  def digest(msg: String, salt: Option[Array[Byte]] = None, alg: DigestAlgorithm): Either[NoSuchAlgorithmException, DigestProvider[IOException]] = {
    digester(alg).right.map {
      md =>
        new {
          def calculate() = {
            salt map {
              s =>
                md.update(s)
            }
            Right(md.digest(msg.getBytes("UTF-8")))
          }
      }
    }
  }

  def digest(is: InputStream, alg: DigestAlgorithm): Either[NoSuchAlgorithmException, DigestProvider[IOException]] = {
    digester(alg).right.map {
      md =>
        new {
          def calculate() = {
            val buf = new Array[Byte](4096) //4KB
            var read = 0;
            try {
              while(read > -1) {
                read = is.read(buf)
                if(read > -1) {
                  md.update(buf)
                }
              }
              Right(md.digest())
            } catch {
              case ioe: IOException =>
                Left(ioe)
            }
          }
        }
    }
  }

  def base64[E <: Exception](dp: DigestProvider[E]): Either[E, String]= dp.calculate().right.map(Base64.toBase64String(_))

  @throws[NoSuchAlgorithmException]("If the requested algorithm is not available by the security provider")
  def base64Unsafe[E <: Exception](edp: Either[NoSuchAlgorithmException, DigestProvider[E]]): String = edp match {
    case Right(dp) =>
      base64(dp) match {
        case Left(e) =>
          throw e
        case Right(v) =>
          v
      }
    case Left(nsae) =>
      throw nsae
  }

  def hex[E <: Exception](dp: DigestProvider[E]): Either[E, String] = dp.calculate().right.map(Hex.toHexString(_))

  @throws[NoSuchAlgorithmException]("If the requested algorithm is not available by the security provider")
  def hexUnsafe[E <: Exception](edp: Either[NoSuchAlgorithmException, DigestProvider[E]]) : String = edp match {
    case Right(dp) =>
      hex(dp) match {
        case Left(e) =>
          throw e
        case Right(v) =>
          v
      }
    case Left(nsae) =>
      throw nsae
  }

}