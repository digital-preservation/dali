/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.auth

import org.scalatra.ScalatraBase
import org.scalatra.auth.{ScentrySupport, ScentryStrategy}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import uk.gov.nationalarchives.dri.preingest.loader.SettingsImpl
import org.scalatra.auth.strategy.{BasicAuthStrategy, BasicAuthSupport}


trait UserPasswordAuthSupport[UserType <: AnyRef] {
  self: (ScalatraBase with ScentrySupport[UserType]) =>

  protected def userPasswordAuth(settings: SettingsImpl)(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    if(!self.isAuthenticated) {
      // if there is a username/password hard-coded in the config, use that instead of ldap
      if (settings.Auth.basicAuthUser.length > 0 && settings.Auth.basicAuthPassword.length > 0) {
        System.err.println("basicAuth")
        if (!ConstantPasswordStrategy.providesAuth(self)) {
          System.err.println("no provider")
          self redirect "/login"
        }
        System.err.println("calling strategy")
        scentry.authenticate(ConstantPasswordStrategy.STRATEGY_NAME)
      } else {
        System.err.println("ldapAuth")
        if(!UserPasswordStrategy.providesAuth(self)) {
          self redirect "/login"
        }
        scentry.authenticate(UserPasswordStrategy.STRATEGY_NAME)
      }
    }
  }
}

// LDAP based login
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

// Configuration based login
class ConstantPasswordStrategy(protected override val app: ScalatraBase, val settings: SettingsImpl)
  extends ScentryStrategy[User] {

  override def name: String = ConstantPasswordStrategy.STRATEGY_NAME

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app redirect("/login")
  }

  protected def validate(userName: String, password: String)
    (implicit request: javax.servlet.http.HttpServletRequest, response: javax.servlet.http.HttpServletResponse): Option[User] = {
    System.err.println("user:" + userName + " pw: " + password + "checku: " + settings.Auth.basicAuthUser + " checkP:"  + settings.Auth.basicAuthPassword)
    if(userName == settings.Auth.basicAuthUser && password == settings.Auth.basicAuthPassword)
      Some(User(1.toString, settings.Auth.basicAuthUser, settings.Auth.basicAuthPassword, None))
    else None
  }

  protected def getUserId(user: User)
     (implicit request: javax.servlet.http.HttpServletRequest, response: javax.servlet.http.HttpServletResponse): String = user.id

  override def isValid(implicit request: HttpServletRequest): Boolean = ConstantPasswordStrategy.providesAuth(app)

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    (app.params.get(UserPasswordStrategy.USERNAME), app.params.get(UserPasswordStrategy.PASSWORD)) match {
      case (Some(username), Some(password)) =>
        validate(username, password)
      case _ =>
        None
    }
  }
}

object ConstantPasswordStrategy {

  val STRATEGY_NAME = "ConstantPassword"

  val USERNAME = "username"
  val PASSWORD = "password"

  def providesAuth(app: ScalatraBase)(implicit request: HttpServletRequest) : Boolean = app.params.get(USERNAME).nonEmpty && app.params.get(PASSWORD).nonEmpty
}