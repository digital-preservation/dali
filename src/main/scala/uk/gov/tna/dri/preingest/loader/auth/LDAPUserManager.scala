package uk.gov.tna.dri.preingest.loader.auth

import com.unboundid.ldap.sdk.{LDAPConnectionOptions, Attribute, SearchScope, LDAPConnection}
import grizzled.slf4j.Logging
import resource._


object LDAPUserManager extends AuthManager[String, User] with Logging {

  val LDAP_SERVER = "web.local" //TODO make configurable
  val LDAP_PORT = 389 //TODO make configurable
  val LDAP_BIND_USER = "USERNAME@web.local" //TODO make configurable
  val LDAP_BIND_PASSWORD = "PASSWORD" //TODO make configurable //TODO do not commit

  val LDAP_SEARCH_BASE = "DC=web,DC=local" //TODO make configurable

  val LDAP_USER_OBJECT_CLASS = "user" //TODO make configurable
  val LDAP_USER_DN_ATTRIBUTE = "distinguishedName" //TODO make configurable
  val LDAP_USER_EMAIL_ATTRIBUTE = "mail" //TODO make configurable
  val LDAP_UID_ATTRIBUTE = "sAMAccountName" //TODO make configurable
  val LDAP_GROUP_ATTRIBUTE = "memberOf" //TODO make configurable
  val LDAP_GROUP = "CN=g-gitlab-users,OU=Global Security Groups,OU=All Groups,DC=web,DC=local" //TODO make configurable

  //TODO make configurable
  val LDAP_OPTS = {
    val opts = new LDAPConnectionOptions()
    opts.setConnectTimeoutMillis(5000)
    opts.setResponseTimeoutMillis(5000)
    opts
  }

  /**
   * Attempts to find a user by DN from LDAP
   */
  def find(key: String): Option[User] = {
    ldapOperation(LDAP_BIND_USER, LDAP_BIND_PASSWORD, findUserByDn(_, key)) match {

      case Left(ts) =>
        ts.map(error("Could not find user by DN in LDAP", _))
        None

      case Right(maybeUserAttrs) =>
        maybeUserAttrs.map {
          case (dn, uid, maybeEmail) =>
            User(dn, uid, "UNKNOWN PASSWORD", maybeEmail)
        }
    }
  }

  /**
   * Validates a user account by username/password against LDAP
   *
   * @param userName The username to validate
   * @param password The password to validate
   *
   * @return A User object representing the user
   */
  def validate(userName: String, password: String): Option[User] = {

    ldapOperation(LDAP_BIND_USER, LDAP_BIND_PASSWORD, findUserDN(_, userName)) match {
      case Left(ts) =>
        ts.map(error("Could not find user in LDAP", _))
        None

      case Right(maybeUserDn) =>
        maybeUserDn.flatMap {
          userDn =>
            ldapOperation(userDn, password, getUser(_, userName)) match {

              case Left(ts) =>
                ts.map(error("Could not retrieve user properties from LDAP", _))
                None

              case Right(userCstr) =>
                userCstr.map(_(password))
            }
        }
    }
  }

  private def findUserByDn(ldap: LDAPConnection, dn: String) = ldapSearch(ldap, ldapUserByDnFilter(dn), Seq(LDAP_UID_ATTRIBUTE, LDAP_USER_EMAIL_ATTRIBUTE)).map(attrs => (dn, attrs(LDAP_UID_ATTRIBUTE).getValue, attrs.get(LDAP_USER_EMAIL_ATTRIBUTE).map(_.getValue)))

  /**
   * Finds a User in LDAP
   * and returns their Distinguished Name
   *
   * @param ldap An LDAP connection which is already bound
   * @param userName
   *
   * @return Some(dn) or None if the user cannot be found in LDAP
   */
  private def findUserDN(ldap: LDAPConnection, userName: String): Option[String] = ldapSearch(ldap, ldapUserFilter(userName), Seq(LDAP_USER_DN_ATTRIBUTE)).flatMap(_.get(LDAP_USER_DN_ATTRIBUTE).map(_.getValue))

  /**
   * Gets a User from LDAP
   *
   * @param ldap An LDAP connection which is already bound
   * @param userName The username of the user to retrieve from LDAP
   *
   * @return A completion function that when given
   *         the users password returns
   *         a User
   */
  private def getUser(ldap: LDAPConnection, userName: String): Option[String => User] = {
    ldapSearch(ldap, ldapUserFilter(userName), Seq(LDAP_USER_DN_ATTRIBUTE, LDAP_USER_EMAIL_ATTRIBUTE)).map {
      attrs =>
        (password: String) =>
          User(attrs(LDAP_USER_DN_ATTRIBUTE).getValue, userName, password, attrs.get(LDAP_USER_EMAIL_ATTRIBUTE).map(_.getValue))
    }
  }

  /**
   * Perform a managed LDAP operation
   *
   * @param bindUser The username binding for connecting to the LDAP
   * @param bindPassword The password for the user binding when connecting to the LDAP
   * @param op A function which operates on an LDAP connection
   *
   * @return Either a sequence of exceptions or the result of $op
   */
  private def ldapOperation[T](bindUser: String, bindPassword: String, op: (LDAPConnection) => T): Either[Seq[Throwable], T] = {
    managed(new LDAPConnection(LDAP_OPTS, LDAP_SERVER, LDAP_PORT, bindUser, bindPassword)).map(op).either
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

  /**
   * Creates an LDAP filter for
   * retrieving a user by userName
   * where they are in a specific group
   *
   * @param userName The username of the user
   *
   * @return The LDAP filter string
   */
  private def ldapUserFilter(userName: String) = s"(&(objectClass=$LDAP_USER_OBJECT_CLASS)($LDAP_UID_ATTRIBUTE=$userName)($LDAP_GROUP_ATTRIBUTE=$LDAP_GROUP))"

  /**
   * Creates an LDAP filter for
   * retrieving a user by Distinguished Name
   * where they are in a specific group
   *
   * @param dn The LDAP Distinguished Name of the user
   *
   * @return The LDAP filter string
   */
  private def ldapUserByDnFilter(dn: String) = s"(&(objectClass=$LDAP_USER_OBJECT_CLASS)($LDAP_USER_DN_ATTRIBUTE=$dn)($LDAP_GROUP_ATTRIBUTE=$LDAP_GROUP))"
}
