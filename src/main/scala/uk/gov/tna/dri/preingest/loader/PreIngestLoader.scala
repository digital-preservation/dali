package uk.gov.tna.dri.preingest.loader

import _root_.akka.actor.{ActorSystem, Props, Actor, ActorRef}
import _root_.akka.pattern._
import _root_.akka.util.Timeout
import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import uk.gov.tna.dri.preingest.loader.auth.LDAPAuthenticationSupport
import scala.slick.session.Database
import org.scalatra.atmosphere._
import org.scalatra.json.{JValueResult, JacksonJsonSupport}
import org.json4s._
import JsonDSL._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.unit._
import org.scalatra.servlet.FileUploadSupport
import uk.gov.tna.dri.preingest.loader.certificate.{ListCertificates, CertificateList, CertificateManagerActor, StoreCertificates}
import uk.gov.tna.dri.preingest.loader.unit.PendingUnit
import org.scalatra.atmosphere.Disconnected
import uk.gov.tna.dri.preingest.loader.unit.ListPendingUnits
import org.scalatra.atmosphere.JsonMessage
import uk.gov.tna.dri.preingest.loader.unit.Register
import org.scalatra.atmosphere.TextMessage
import scala.Some
import uk.gov.tna.dri.preingest.loader.unit.DeRegister
import uk.gov.tna.dri.preingest.loader.unit.PendingUnits
import org.scalatra.atmosphere.Error


class PreIngestLoader(system: ActorSystem, preIngestLoaderActor: ActorRef) extends ScalatraServlet
  with ScalateSupport with JValueResult
  with JacksonJsonSupport
  with SessionSupport
  with FileUploadSupport
  with AtmosphereSupport
  with FutureSupport
  with LDAPAuthenticationSupport {

  implicit protected val jsonFormats: Formats = DefaultFormats
  protected implicit def executor: ExecutionContext = system.dispatcher

  lazy val certificateManagerActor = system.actorOf(Props[CertificateManagerActor], name="certificateManagerActor")

  def toJson(cl: CertificateList) : JValue = {
    ("certificate" ->
      cl.certificates.map{
        certificate =>
          ("name" -> certificate)
      }
    )
  }

  import org.scalatra.ActionResult._

  get("/") {
    userPasswordAuth
    contentType = "text/html"
    ssp("home", "title" -> "DRI Pre-Ingest Loader", "username" -> user.username)
  }

  //TODO ideally would be implemented with web-sockets
  post("/certificate") {
    userPasswordAuth
    fileMultiParams.get("certificate") match {

      case Some(certificates) =>
        certificateManagerActor ! StoreCertificates(user.username, certificates.map(c => c.getName -> c.get)) //NOTE we extract the data with c.get here, otherwise jetty deletes the underlying file when the response is returned below
        Accepted()

      case None =>
        BadRequest(<p>No Certificate files were sent!</p>)
    }
  }


  //TODO ideally would be implemented with web-sockets
  get("/certificate") {
    userPasswordAuth
    new AsyncResult {
      val is = ask(certificateManagerActor, ListCertificates(user.username))(Timeout(10 seconds)).mapTo[CertificateList].map(toJson(_))
    }
  }

  /* start security uri */
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
  /* end security uri */

//  def test(client: AtmosphereClient) : Boolean = {
//    println("testing client: " + client.uuid)
//    true
//  }

//  atmosphere("/certificate/:filename") {
//    val filename = params("filename")
//
//    new AtmosphereClient {
//      def receive = {
//        case TextMessage(text) if(!text.isEmpty) =>
//          val x = text.getBytes()
//          println(s"filename: ${filename} Received text: $text")
//      }
//    }
//  }

  atmosphere("/unit") {
    userPasswordAuth

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
                  preIngestLoaderActor ! ListPendingUnits(uuid)

                case u =>
                  println("unknown action: " + u)
              }
          }
       }
    }
  }
}

class PreIngestLoaderActor(pendingUnitsActor: ActorRef) extends Actor with Logging {

  pendingUnitsActor ! Listen

  def receive = {
    case lpu: ListPendingUnits =>
        pendingUnitsActor ! lpu

    case PendingUnits(clientId, pendingUnits) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("pending", pendingUnits)), _.uuid == clientId) //send only to client

    case Register(pendingUnit) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("pendingAdd", pendingUnit))) //update everyone

    case DeRegister(pendingUnit) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("pendingRemove", pendingUnit))) //update everyone
  }

  def toJson(action: String, pendingUnit: PendingUnit) : JValue = toJson(action, List(pendingUnit))

  def toJson(action: String, pendingUnits: List[PendingUnit]) : JValue = {
    (action ->
      ("unit" ->
        pendingUnits.map {
          pendingUnit =>
            ("interface" -> pendingUnit.interface) ~
            ("src" -> pendingUnit.src) ~
            ("label" -> pendingUnit.label) ~
            ("size" -> pendingUnit.size) ~
            ("timestamp" -> pendingUnit.timestamp)
        }
      )
    )
  }
}