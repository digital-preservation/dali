package uk.gov.tna.dri.preingest.loader.store

import scalax.file.Path
import java.security.{Security, MessageDigest}
import org.bouncycastle.util.encoders.Base64

object DataStore {

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  private lazy val settingsDir = Path.fromString(sys.props("user.home")) / ".dri-loader" //TODO make configurable

  /**
   * Path to a KeyStore for a user
   *
   * Generates a KeyStore path based on the username
   */
  def userStore(username: String) : Path = {
    val md = MessageDigest.getInstance("RIPEMD320")
    val dUsername = Base64.toBase64String(md.digest(username.getBytes("UTF-8")))
    val store = settingsDir / dUsername
    if(!store.exists) {
      store.doCreateDirectory()
    }
    store
  }
}
