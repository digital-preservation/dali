package uk.gov.tna.dri.preingest.loader.store

import scalax.file.Path
import uk.gov.tna.dri.preingest.loader.Crypto
import uk.gov.tna.dri.preingest.loader.Crypto.DigestAlgorithm

object DataStore {

  private lazy val settingsDir = Path.fromString(sys.props("user.home")) / ".dri-loader" //TODO make configurable

  /**
   * Path to a KeyStore for a user
   *
   * Generates a KeyStore path based on the username
   */
  def userStore(username: String) : Path = {
    val dUsername = Crypto.base64Unsafe(Crypto.digest(username, None, DigestAlgorithm.RIPEMD320))
    val store = settingsDir / dUsername
    if(!store.exists) {
      store.doCreateDirectory()
    }
    store
  }

  def withTemporaryFile[T](fileDetail: Option[(String, Array[Byte])])(f: Option[Path] => T) : T = fileDetail match {
    case Some((name, data)) =>
      val tmpFile = Path.createTempFile(deleteOnExit = true)
      try {
        tmpFile.write(data)
        f(Option(tmpFile))
      } finally {
        tmpFile.delete(force = true)
      }
    case None =>
      f(None)
  }

  def isWindowsJunkDir(name: String) = name.matches("System Volume Information|^Recycler.*|^\\..+")
}
