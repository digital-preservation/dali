package uk.gov.tna.dri.preingest.loader.auth

import org.scalatra.ScalatraBase
import org.scalatra.auth.{ScentrySupport, ScentryStrategy}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

trait UserPasswordAuthSupport[UserType <: AnyRef] {
  self: (ScalatraBase with ScentrySupport[UserType]) =>

  protected def userPasswordAuth()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    if(!self.isAuthenticated) {
      if(!UserPasswordStrategy.providesAuth(self)) {
        self redirect "/login"
      }
      scentry.authenticate(UserPasswordStrategy.STRATEGY_NAME)
    }
  }
}

class UserPasswordStrategy(protected override val app: ScalatraBase) extends ScentryStrategy[User] {

  override def name: String = UserPasswordStrategy.STRATEGY_NAME

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app redirect("/login")
  }

  override def isValid(implicit request: HttpServletRequest): Boolean = UserPasswordStrategy.providesAuth(app)

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    (app.params.get(UserPasswordStrategy.USERNAME), app.params.get(UserPasswordStrategy.PASSWORD)) match {
      case (Some(username), Some(password)) =>
        LDAPUserManager.validate(username, password)
      case _ =>
        None
    }
  }
}

object UserPasswordStrategy {

  val STRATEGY_NAME = "UserPassword"

  val USERNAME = "username"
  val PASSWORD = "password"

  def providesAuth(app: ScalatraBase)(implicit request: HttpServletRequest) : Boolean = app.params.get(USERNAME).nonEmpty && app.params.get(PASSWORD).nonEmpty
}
