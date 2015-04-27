/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.auth

import java.util.UUID
import com.mchange.v2.c3p0.ComboPooledDataSource
import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession

object RememberMeDb {

  lazy val cpds = new ComboPooledDataSource
  lazy val db = Database.forDataSource(cpds)
  val mapping = TableQuery[Mapping]

  withDb {
    mapping.ddl.create
  }

  private def withDb[B](f: => B) : B = {
    db withDynSession {
      // The session is never named explicitly. It is bound to the current
      // thread as the dynamicSession that we imported
      f
    }
  }

  def close {
    cpds.close
  }

  def +(user: User) : String = {
    withDb {
      val token = UUID.randomUUID().toString
      mapping.insert(user.id, token)
      token
    }
  }

  def ?(token: String) : Option[String] = {
    withDb {
     // Query(mapping).filter(_.token === token).firstOption.map(_._1)
      mapping.filter(_.token === token).firstOption.map(_._1)

    }
  }

  def -(user: User) {
    withDb {
      mapping.filter(_.id === user.id).delete
    }
  }
}

class Mapping(tag: Tag) extends Table[(String, String)](tag, "remember_me_mapping") {
  def id = column[String]("id", O.PrimaryKey)
  def token = column[String]("token")
  def * = (id, token)
}
