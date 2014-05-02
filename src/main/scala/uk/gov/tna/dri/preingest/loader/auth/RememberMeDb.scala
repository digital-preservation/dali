package uk.gov.tna.dri.preingest.loader.auth

import java.util.UUID
import com.mchange.v2.c3p0.ComboPooledDataSource
import scala.slick.session.Database
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

object RememberMeDb {

  lazy val cpds = new ComboPooledDataSource
  lazy val db = Database.forDataSource(cpds)

  withDb {
    Mapping.ddl.create
  }

  private def withDb[B](f: => B) : B = {
    db withSession {
      // The session is never named explicitly. It is bound to the current
      // thread as the threadLocalSession that we imported
      f
    }
  }

  def close {
    cpds.close
  }

  def +(user: User) : String = {
    withDb {
      val token = UUID.randomUUID().toString
      Mapping.insert(user.id, token)
      token
    }
  }

  def ?(token: String) : Option[String] = {
    withDb {
      Query(Mapping).filter(_.token === token).firstOption.map(_._1)
    }
  }

  def -(user: User) {
    withDb {
      Query(Mapping).filter(_.id === user.id).delete
    }
  }
}

object Mapping extends Table[(String, String)]("remember_me_mapping") {
  def id = column[String]("id", O.PrimaryKey)
  def token = column[String]("token")
  def * = id ~ token
}