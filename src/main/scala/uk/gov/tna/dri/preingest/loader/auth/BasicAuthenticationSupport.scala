/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.auth

import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import org.scalatra.ScalatraBase
import uk.gov.nationalarchives.dri.preingest.loader.SettingsImpl

trait BasicAuthenticationSupport extends AuthenticationSupport {
  self: ScalatraBase =>


  override protected def fromSession = {
    case id: String =>
      User(id, settings.Auth.basicAuthUser, settings.Auth.basicAuthPassword, None)
  }

  override protected def registerAuthStrategies = {
    super.registerAuthStrategies
    scentry.register(new ConstantPasswordStrategy(self, settings))
  }

}
