/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.tna.dri.preingest.loader

object UserErrorMessages {
  type UserErrorMessage = String

  val DECRYPT_NON_ENCRYPTED = "You cannot decrypt a non-encrypted unit"

  def NO_CERTIFICATE(action: String, id: String, certName: String) = s"Whilst $action for $id, the certificate $certName could not be retrieved"

  def ALREADY_EXISTS(path: String) = s"File already exists $path"
}
