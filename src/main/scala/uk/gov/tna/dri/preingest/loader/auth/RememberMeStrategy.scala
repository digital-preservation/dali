package uk.gov.tna.dri.preingest.loader.auth

import org.scalatra.{CookieOptions, ScalatraBase}
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import scala.concurrent.duration._
import uk.gov.tna.dri.preingest.loader.SettingsImpl

class RememberMeStrategy(protected override val app: ScalatraBase, val settings: SettingsImpl) extends ScentryStrategy[User] {

  override def name: String = "RememberMe"

  override def isValid(implicit request: HttpServletRequest): Boolean = app.cookies.get(settings.Auth.rememberMeCookieKey).nonEmpty

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    if(winningStrategy == "RememberMe" ||
      (winningStrategy == "UserPassword" && app.params.get("rememberMe").getOrElse("false") == "true")) {

      app.cookies.set(settings.Auth.rememberMeCookieKey, RememberMeDb + user)(CookieOptions(secure = false, maxAge = settings.Auth.rememberMeCookieLifetime.toSeconds.toInt, httpOnly = true))
    }
  }

  override def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    app.cookies.get(settings.Auth.rememberMeCookieKey).flatMap {
      token =>
        RememberMeDb ? token flatMap {
          userId =>
            LDAPUserManager.find(userId)
        }
    }
  }

  /**
   * Clears the remember-me cookie for the specified user.
   */
  override def beforeLogout(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app.cookies.delete(settings.Auth.rememberMeCookieKey)
    if(user != null) {
      RememberMeDb - user
    }
  }
}
