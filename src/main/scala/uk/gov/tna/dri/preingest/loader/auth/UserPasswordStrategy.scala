/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.tna.dri.preingest.loader.auth

import org.scalatra.ScalatraBase
import org.scalatra.auth.{ScentrySupport, ScentryStrategy}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import uk.gov.tna.dri.preingest.loader.SettingsImpl

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

class UserPasswordStrategy(protected override val app: ScalatraBase, val settings: SettingsImpl)
  extends ScentryStrategy[User]
  with LDAPUserManager {

  override def name: String = UserPasswordStrategy.STRATEGY_NAME

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app redirect("/login")
  }

  override def isValid(implicit request: HttpServletRequest): Boolean = UserPasswordStrategy.providesAuth(app)

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    (app.params.get(UserPasswordStrategy.USERNAME), app.params.get(UserPasswordStrategy.PASSWORD)) match {
      case (Some(username), Some(password)) =>
        validate(username, password)
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
