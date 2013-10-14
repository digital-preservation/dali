package uk.gov.tna.dri.preingest.loader

import org.scalatra._
import java.net.URL
import org.scalatra.scalate.ScalateSupport
import uk.gov.tna.dri.preingest.loader.auth.LDAPAuthenticationSupport
import scala.slick.session.Database


class PreIngestLoader extends ScalatraServlet with ScalateSupport with LDAPAuthenticationSupport {

  get("/") {
    userPasswordAuth
    contentType = "text/html"
    ssp("home", "title" -> "DRI Pre-Ingest Loader", "username" -> this.userOption.map(_.username).getOrElse(""))
  }

  get("/login") {
    contentType = "text/html"
    ssp("login", "title" -> "DRI Login")
  }

  post("/login") {
    userPasswordAuth
    redirect("/")
  }

  get("/logout") {
    logOut
    redirect("/")
  }

  /*
   get("/woof") {
     contentType="text/html"
    ssp("woof.ssp","date" -> new java.util.Date)
  } 
  
  case class Flower(slug: String, name: String) {
    def toXML= <flower name={name}>{slug}</flower>
  }
  
   val all = List(
      Flower("yellow-tulip", "Yellow Tulip"),
      Flower("red-rose", "Red & Rose"),
      Flower("black-rose", "Black Rose"))
   
  get("/flowers"){
     contentType="text/xml"
    <flowers> 
      { all.map(_.toXML) }
     </flowers>
  }*/
   
}