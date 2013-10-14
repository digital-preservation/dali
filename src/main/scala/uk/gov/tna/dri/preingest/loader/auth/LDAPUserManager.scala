package uk.gov.tna.dri.preingest.loader.auth


object LDAPUserManager extends AuthManager[Int, User] {

  val testUser = User(1, "adam", "adam")

  //TODO implement
  def find(key: Int): Option[User] = if(key == 1) Some(testUser) else None

  //TODO implement
  def validate(userName: String, password: String) : Option[User] = if(userName == "adam" && password=="adam") Some(testUser) else None
}
