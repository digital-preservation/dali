package uk.gov.tna.dri.preingest.loader.auth

import com.unboundid.ldap.sdk.{LDAPConnectionOptions, Attribute, SearchScope, LDAPConnection}
import grizzled.slf4j.Logging
import resource._
import uk.gov.tna.dri.preingest.loader.SettingsImpl

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
trait LDAPUserManager extends AuthManager[String, User] with Logging {

  protected val settings: SettingsImpl

  /**
   * Simple Encapsulation of
   * round-robin use of a list
   * of LDAP servers
   */
  private object RoundRobinLdap {
    private var next = settings.Auth.ldapServer

    /**
     * Get the next server
     * from the round-robin
     *
     * @return The next server
     */
    def nextServer() = synchronized {
      next match {
        case head :: tail =>
          next = tail
          head
        case Nil =>
          next = settings.Auth.ldapServer.tail
          settings.Auth.ldapServer.head
      }
    }
  }

  /**
   * Attempts to find a user by DN from LDAP
   */
  def find(key: String): Option[User] = {
    ldapOperation(settings.Auth.ldapBindUser, settings.Auth.ldapBindPassword, findUserByDn(_, key)) match {

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

    ldapOperation(settings.Auth.ldapBindUser, settings.Auth.ldapBindPassword, findUserDN(_, userName)) match {
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

  private def findUserByDn(ldap: LDAPConnection, dn: String) = ldapSearch(ldap, ldapUserByDnFilter(dn), Seq(settings.Auth.ldapUserAttributeUid, settings.Auth.ldapUserAttributeEmail)).map(attrs => (dn, attrs(settings.Auth.ldapUserAttributeUid).getValue, attrs.get(settings.Auth.ldapUserAttributeEmail).map(_.getValue)))

  /**
   * Finds a User in LDAP
   * and returns their Distinguished Name
   *
   * @param ldap An LDAP connection which is already bound
   * @param userName
   *
   * @return Some(dn) or None if the user cannot be found in LDAP
   */
  private def findUserDN(ldap: LDAPConnection, userName: String): Option[String] = ldapSearch(ldap, ldapUserFilter(userName), Seq(settings.Auth.ldapUserAttributeDN)).flatMap(_.get(settings.Auth.ldapUserAttributeDN).map(_.getValue))

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
    ldapSearch(ldap, ldapUserFilter(userName), Seq(settings.Auth.ldapUserAttributeDN, settings.Auth.ldapUserAttributeEmail)).map {
      attrs =>
        (password: String) =>
          User(attrs(settings.Auth.ldapUserAttributeDN).getValue, userName, password, attrs.get(settings.Auth.ldapUserAttributeEmail).map(_.getValue))
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
    val opts = new LDAPConnectionOptions()
    opts.setConnectTimeoutMillis(settings.Auth.ldapTimeoutConnection)
    opts.setResponseTimeoutMillis(settings.Auth.ldapTimeoutRequest)

    managed(new LDAPConnection(opts, RoundRobinLdap.nextServer(), settings.Auth.ldapPort, bindUser, bindPassword)).map(op).either
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
    val searchResults = ldap.search(settings.Auth.ldapSearchBase, SearchScope.SUB, filter, attributes: _*)

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
  private def ldapUserFilter(userName: String) = s"(&(objectClass=${settings.Auth.ldapUserObjectClass})(${settings.Auth.ldapUserAttributeUid}=$userName)(${settings.Auth.ldapUserAttributeGroupMembership}=${settings.Auth.ldapApplicationGroup}))"

  /**
   * Creates an LDAP filter for
   * retrieving a user by Distinguished Name
   * where they are in a specific group
   *
   * @param dn The LDAP Distinguished Name of the user
   *
   * @return The LDAP filter string
   */
  private def ldapUserByDnFilter(dn: String) = s"(&(objectClass=${settings.Auth.ldapUserObjectClass})(${settings.Auth.ldapUserAttributeDN}=$dn)(${settings.Auth.ldapUserAttributeGroupMembership}=${settings.Auth.ldapApplicationGroup}))"
}
