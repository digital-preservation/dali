package uk.gov.tna.dri.preingest.loader.auth

case class User(id: String, username: String, password: String, email: Option[String])
