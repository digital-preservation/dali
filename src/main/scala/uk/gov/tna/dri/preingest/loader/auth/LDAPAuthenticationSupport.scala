/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.tna.dri.preingest.loader.auth

import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import org.scalatra.ScalatraBase
import uk.gov.tna.dri.preingest.loader.SettingsImpl

trait LDAPAuthenticationSupport extends ScentrySupport[User]
  with UserPasswordAuthSupport[User]
  with LDAPUserManager {
  self: ScalatraBase =>

  protected val settings: SettingsImpl

  protected def fromSession = {
    case id: String =>
      find(id).getOrElse(null)
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
    scentry.register(new UserPasswordStrategy(self, settings))
    scentry.register(new RememberMeStrategy(self, settings))
  }

}
