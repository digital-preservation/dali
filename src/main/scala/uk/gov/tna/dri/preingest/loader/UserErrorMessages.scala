package uk.gov.tna.dri.preingest.loader

object UserErrorMessages {
  type UserErrorMessage = String

  val DECRYPT_NON_ENCRYPTED = "You cannot decrypt a non-encrypted unit"

  def NO_CERTIFICATE(action: String, id: String, certName: String) = s"Whilst $action for $id, the certificate $certName could not be retrieved"
}
