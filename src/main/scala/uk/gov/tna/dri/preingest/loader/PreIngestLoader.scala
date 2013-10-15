package uk.gov.tna.dri.preingest.loader

import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import uk.gov.tna.dri.preingest.loader.auth.LDAPAuthenticationSupport
import scala.slick.session.Database
import org.scalatra.atmosphere._
import org.scalatra.json.{JValueResult, JacksonJsonSupport}
import org.json4s._
import JsonDSL._
import scala.concurrent._
import ExecutionContext.Implicits.global


class PreIngestLoader extends ScalatraServlet
  with ScalateSupport with JValueResult
  with JacksonJsonSupport
  with SessionSupport
  with AtmosphereSupport
  with LDAPAuthenticationSupport {

  implicit protected val jsonFormats: Formats = DefaultFormats


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

  def test(client: AtmosphereClient) : Boolean = {
    println("testing client: " + client.uuid)
    true
  }

  atmosphere("/unit") {
    new AtmosphereClient {
         def receive = {

           case Connected =>
             println(s"Client $uuid is connected")

           case Disconnected(disconnector, Some(error)) =>
             println(s"Client $uuid disconnected: " +  error)

           case Error(Some(error)) =>
             println("ERROR: " + error)

           case TextMessage(text) =>
             println("RECEIVED TEXT: " + text)

           case JsonMessage(json) =>

            println("RECEIVED JSON: " + json)

            json match {
              case JObject(List(("action", JString(jValue)))) =>
                jValue match {
                  case "pending" =>

                  case u =>
                    println("unknown action: " + u)
                }
            }


         }
    }
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