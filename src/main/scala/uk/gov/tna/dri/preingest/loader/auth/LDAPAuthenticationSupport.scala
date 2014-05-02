package uk.gov.tna.dri.preingest.loader.auth

import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import org.scalatra.ScalatraBase

trait LDAPAuthenticationSupport extends ScentrySupport[User] with UserPasswordAuthSupport[User] {
  self: ScalatraBase =>


  protected def fromSession = {
    case id: String =>
      LDAPUserManager.find(id).getOrElse(null)
  }
  protected def toSession = {
    case user: User =>
      user.id
  }
  protected val scentryConfig = (new ScentryConfig{}).asInstanceOf[ScentryConfiguration]

  override protected def configureScentry = {
    scentry.unauthenticated {
      scentry.strategies(UserPasswordStrategy.STRATEGY_NAME).unauthenticated()
    }
  }

  override protected def registerAuthStrategies = {
    scentry.register(new UserPasswordStrategy(self))
    scentry.register(new RememberMeStrategy(self))

    //scentry.register("UserPassword", app ⇒ new UserPasswordStrategy(app))
    //scentry.register("RememberMe", app ⇒ new RememberMeStrategy(app))
  }

}
