package uk.gov.tna.dri.preingest.loader.auth

import com.unboundid.ldap.sdk.{Attribute, SearchScope, LDAPConnection}
import resource._


object LDAPUserManager extends AuthManager[String, User] {

  val LDAP_SERVER = "web.local" //TODO make configurable
  val LDAP_PORT = 389 //TODO make configurable
  val LDAP_BIND_USER = "uid=s-gitlab,ou=Service Accounts,ou=All Users,dc=web,dc=local" //TODO make configurable
  val LDAP_BIND_PASSWORD = "t0rval5" //TODO make configurable //TODO do not commit

  val LDAP_SEARCH_BASE = "DC=web,DC=local" //TODO make configurable

  val LDAP_USER_OBJECT_CLASS = "user" //TODO make configurable
  val LDAP_USER_DN_ATTRIBUTE = "distinguishedName" //TODO make configurable
  val LDAP_USER_EMAIL_ATTRIBUTE = "mail" //TODO make configurable
  val LDAP_UID_ATTRIBUTE = "sAMAccountName" //TODO make configurable
  val LDAP_GROUP_ATTRIBUTE = "memberOf" //TODO make configurable
  val LDAP_GROUP = "CN=g-gitlab-users,OU=Global Security Groups,OU=All Groups,DC=web,DC=local" //TODO make configurable

  def find(key: String): Option[User] = {
    ldapOperation(LDAP_BIND_USER, LDAP_BIND_PASSWORD, findUserByDn(_, key)) match {

      case Left(ts) =>
        //TODO log
        None

      case Right(maybeUserAttrs) =>
        maybeUserAttrs.map {
          case (dn, uid, maybeEmail) =>
            User(dn, uid, "UNKNOWN PASSWORD", maybeEmail)
        }
    }
  }

  def validate(userName: String, password: String) : Option[User] = {

    ldapOperation(LDAP_BIND_USER, LDAP_BIND_PASSWORD, findUser(_, userName)) match {
      case Left(ts) =>
        //TODO log
        None

      case Right(maybeUserDn) =>
        maybeUserDn.flatMap {
          userDn =>
            ldapOperation(userDn.getValue, password, getPartialUserFromAttributes(_, userName)) match {

              case Left(t) =>
                //TODO log
                None

              case Right(userCstr) =>
                userCstr.map(_(password))
            }
        }
    }
  }

  private def findUserByDn(ldap: LDAPConnection, dn: String) = ldapSearch(ldap, ldapUserByDnFilter(dn), Seq(LDAP_UID_ATTRIBUTE, LDAP_USER_EMAIL_ATTRIBUTE)).map(attrs => (dn, attrs(LDAP_UID_ATTRIBUTE).getValue, attrs.get(LDAP_USER_EMAIL_ATTRIBUTE).map(_.getValue)))

  private def findUser(ldap: LDAPConnection, userName: String) = ldapSearch(ldap, ldapUserFilter(userName), Seq(LDAP_USER_DN_ATTRIBUTE)).flatMap(_.get(LDAP_USER_DN_ATTRIBUTE))

  private def getPartialUserFromAttributes(ldap: LDAPConnection, userName: String): Option[String => User] = {
    ldapSearch(ldap, ldapUserFilter(userName), Seq(LDAP_USER_DN_ATTRIBUTE, LDAP_USER_EMAIL_ATTRIBUTE)).map {
      attrs =>
        (password: String) =>
          User(attrs(LDAP_USER_DN_ATTRIBUTE).getValue, userName, password, attrs.get(LDAP_USER_EMAIL_ATTRIBUTE).map(_.getValue))
    }
  }

  private def ldapOperation[T](bindUser: String, bindPassword: String, op: (LDAPConnection) => T): Either[Seq[Throwable], T] = {
    managed(new LDAPConnection(LDAP_SERVER, LDAP_PORT, bindUser, bindPassword)).map(op).either
  }

  /**
   * Perform an LDAP search on a number of attributes
   *
   * @param ldap Connection to the LDAP
   * @param filter Filter to search
   * @param attributes Attributes to return from the search result classes
   *
   * @return Map of key/value where the keys is the requested attributes, or None if no search results
   */
  private def ldapSearch(ldap: LDAPConnection, filter: String, attributes: Seq[String]) : Option[Map[String, Attribute]] = {
    val searchResults = ldap.search(LDAP_SEARCH_BASE, SearchScope.SUB, filter, attributes: _*)

    if(searchResults.getEntryCount() > 0) {
      val entry = searchResults.getSearchEntries().get(0)

      Some(attributes map {
        attribute =>
          (attribute -> entry.getAttribute(attribute))
      } toMap)
    } else {
      None
    }
  }

  private def ldapUserFilter(userName: String) = s"(&(objectClass=$LDAP_USER_OBJECT_CLASS)($LDAP_UID_ATTRIBUTE=$userName)($LDAP_GROUP_ATTRIBUTE=$LDAP_GROUP))"

  private def ldapUserByDnFilter(dn: String) = s"(&(objectClass=$LDAP_USER_OBJECT_CLASS)($LDAP_USER_DN_ATTRIBUTE=$dn)($LDAP_GROUP_ATTRIBUTE=$LDAP_GROUP))"
}
