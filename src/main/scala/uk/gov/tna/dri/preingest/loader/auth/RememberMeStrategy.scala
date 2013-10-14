package uk.gov.tna.dri.preingest.loader.auth

import org.scalatra.{CookieOptions, ScalatraBase}
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import scala.concurrent.duration._

class RememberMeStrategy(protected override val app: ScalatraBase) extends ScentryStrategy[User] {

  val COOKIE_KEY = "dri.preingest.loader.rememberMe"
  val ONE_WEEK = (7 days).toSeconds.toInt

  override def name: String = "RememberMe"

  override def isValid(implicit request: HttpServletRequest): Boolean = app.cookies.get(COOKIE_KEY).nonEmpty

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    if(winningStrategy == "RememberMe" ||
      (winningStrategy == "UserPassword" && app.params.get("rememberMe").getOrElse("false") == "true")) {

      app.cookies.set(COOKIE_KEY, RememberMeDb + user)(CookieOptions(secure = false, maxAge = ONE_WEEK, httpOnly = true))
    }
  }

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    app.cookies.get(COOKIE_KEY).flatMap {
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
    app.cookies.delete(COOKIE_KEY)
    if(user != null) {
      RememberMeDb - user
    }
  }
}
