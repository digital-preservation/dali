package uk.gov.tna.dri.preingest.loader.auth


object LDAPUserManager extends AuthManager[Int, User] {

  val testUsers = Map(
    1 -> User(1, "adam", "adam"),
    2 -> User(2, "aretter", "aretter")
  )

  //TODO implement for ldap
  def find(key: Int): Option[User] = testUsers.get(key)

  //TODO implement for ldap
  def validate(userName: String, password: String) : Option[User] = testUsers.values.collectFirst {
    case user if(user.username == userName && user.password == password) =>
      user
  }
}
