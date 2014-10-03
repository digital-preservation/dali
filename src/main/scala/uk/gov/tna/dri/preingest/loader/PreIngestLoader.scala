package uk.gov.tna.dri.preingest.loader

import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import akka.pattern._
import akka.util.Timeout
import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import uk.gov.tna.dri.preingest.loader.auth.{User, LDAPAuthenticationSupport}
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
import uk.gov.tna.dri.preingest.loader.certificate.CertificateList
import org.scalatra.atmosphere.Disconnected
import org.scalatra.atmosphere.JsonMessage
import org.scalatra.atmosphere.TextMessage
import scala.Some
import uk.gov.tna.dri.preingest.loader.certificate.ListCertificates
import org.scalatra.atmosphere.Error
import uk.gov.tna.dri.preingest.loader.certificate.StoreCertificates
import uk.gov.tna.dri.preingest.loader.catalogue.LoaderCatalogueJmsClient
import uk.gov.tna.dri.catalogue.jms.client.JmsConfig
import uk.gov.nationalarchives.dri.catalogue.api.ingest.{PartIdType, MediaType, DriUnitType}
import scala.collection.mutable


class PreIngestLoader(system: ActorSystem, preIngestLoaderActor: ActorRef, certificateManagerActor: ActorRef) extends ScalatraServlet
  with ScalateSupport with JValueResult
  with JacksonJsonSupport
  with SessionSupport
  with FileUploadSupport
  with AtmosphereSupport
  with FutureSupport
  with LDAPAuthenticationSupport
  with Logging {

  protected val settings = Settings(system)

  implicit protected val jsonFormats: Formats = DefaultFormats
  protected implicit def executor: ExecutionContext = system.dispatcher

  def toJson(cl: CertificateList) : JValue = {
    ("certificate" ->
      cl.certificates.map{
        certificate =>
          ("name" -> certificate)
      }
    )
  }


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

//TODO trying to use atmosphere for file upload, needs Scalatra 2.3.x (Adam sent them a pull-request!)
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
    val x: User = user  //TODO this is not very safe! what happens when the user's session expires etc!

    new AtmosphereClient {
       def receive = {

         case Connected =>
          info(s"Client $uuid is connected")

         case Disconnected(disconnector, Some(error)) =>
          warn(s"Client $uuid disconnected: " +  error)

         case Error(Some(err)) =>
          error(err)

         case TextMessage(text) =>
           debug("RECEIVED TEXT: " + text)

         case JsonMessage(json) =>
          debug("RECEIVED JSON: " + json)

          //val username = user.username //TODO causes NPE at the moment
          val username = x.username //TODO fix above, this is a temp solution

          import uk.gov.tna.dri.preingest.loader.ClientAction.Actions
          try{
            val clientActions = json.extract[Actions]
            clientActions.actions map {
              a =>
                a.action match {
                  case("pending") =>
                    preIngestLoaderActor ! ListUnits(uuid)
                  case("decrypt") =>
                    preIngestLoaderActor ! UpdateUnitDecryptDetail(username, a.unitRef.get.uid, a.certificate, a.passphrase.get, Option(uuid))
                  case("checkCatalogued") =>
                    val parts = a.loadUnit.get.parts.map(p => Part(p.unit, p.series))
                    preIngestLoaderActor !  PartsCatalogued(a.loadUnit.get.uid, parts)
                  case("loadEncrypted") =>
                    val parts = a.loadUnit.get.parts.map(p => TargetedPart(Destination.withName(p.destination), p.fixity, Part(p.unit, p.series)))
                    preIngestLoaderActor ! LoadUnit(username, a.loadUnit.get.uid, parts, a.certificate, a.passphrase, Option(uuid), Option(preIngestLoaderActor))
                  case("loadUnencrypted") =>
                    val parts = a.loadUnit.get.parts.map(p => TargetedPart(Destination.withName(p.destination), p.fixity, Part(p.unit, p.series)))
                    preIngestLoaderActor ! LoadUnit(username, a.loadUnit.get.uid, parts, None, None, Option(uuid), Option(preIngestLoaderActor))
                  case("loaded") =>
                    preIngestLoaderActor ! GetLoaded(a.limit.get)
                  //case(_) => ??? // throws exception (should never be reached, but needed to keep compiler warning quiet)
              }
            }
          } catch {
            case (t: Throwable) => logger.error("there was a problem ", t)
          }
       }
    }
  }
}

// The PreIngestLoaderActor is responsible for external communications only; ie messages to catalogue and to browser
class PreIngestLoaderActor extends Actor with Logging {

  import scala.collection.JavaConverters._

  val unitManagerActor = context.actorOf(Props[UnitManagerActor], name="unitManagerActor")
  unitManagerActor ! Listen

  val settings = Settings(context.system).JmsConfig
  val jmsConfig = new JmsConfig(settings.brokerName, settings.username, settings.password, settings.queueName, settings.timeout.toLong)
  lazy val jmsClient = new LoaderCatalogueJmsClient(jmsConfig)

  val of = new uk.gov.nationalarchives.dri.catalogue.api.ingest.ObjectFactory

  def receive = {

    //send unit status (i.e. add of update)
    case UnitStatus(unit, action, clientId) => {
      clientId match {
        case Some(clientId) => AtmosphereClient.broadcast("/unit", JsonMessage(toJson("update", unit)), new OnlySelf(clientId))
        case None => AtmosphereClient.broadcast("/unit", JsonMessage(toJson("update", unit)), new Everyone)
      }
    }
    // notify browser of need to display fixity progress bar if 0, of progress otherwise
    case PartFixityProgress(part, fixityProgressPercentage) => {
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("fixityprogress", part.unitId, fixityProgressPercentage)))
    }

    // check for presence of parts to load in catalogue, and notify browser if failed
    case PartsCatalogued(uid, parts) => {
      val partIdType = of.createPartIdType
      parts.map(p => {
        partIdType.setSeries(p.series)
        partIdType.setUnitId(p.unitId)
        jmsClient.getCataloguePartStatus(partIdType) match {
          // should have status Identified
          case Some(status) =>  logger.info("Part " + partIdType + " has status " + status)
          case None =>  AtmosphereClient.broadcast("/unit", JsonMessage(toJson("error", p.unitId, p.unitId, "Part " + p.series + " not in catalogue")))
        }
      })
    }

    case UnitProgress(unit, parts, progressPercentage) => {
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("progress", unit.uid, progressPercentage)))
      if (progressPercentage >= 100) {
        // update catalogue status of all parts
        val partIdType = of.createPartIdType
        parts.map(p => {
          partIdType.setSeries(p.part.series)
          partIdType.setUnitId(p.part.unitId)
          val destString = getDestString(p)
          logger.info("Updating catalog  partIdType  "+  partIdType + "loaded to " + destString)
          jmsClient.updateCataloguePartStatus("partLoadedTo" + destString, partIdType, "Part loaded to " + destString)
        })
        // update catalogue unit status
        val unitIdType = of.createUnitIdType
        // temporary hack: unit.uid is UUID, not a UnitId, need to add UnitId to units
        // in the meantime, retrieve it from the first part
        unitIdType.setUnitId(parts(0).part.unitId)
        jmsClient.updateCatalogueUnitStatus("unitLoaded", unitIdType, "Unit loaded")
      }
    }
    case PartFixityError(part, errorMessage) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("error", part.unitId, part.series, errorMessage)))

    case UnitError(unit, errorMessage) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("error", unit.uid, unit.label, errorMessage)))

    //remove unit detail
    case DeRegisterUnit(unitUid) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("remove", unitUid)))

    //list all unit status
    case lu: ListUnits =>
      unitManagerActor ! lu

    case uudd: UpdateUnitDecryptDetail =>
      unitManagerActor ! uudd

    case lu: LoadUnit =>
      unitManagerActor ! lu

    case GetLoaded(limit) =>
      jmsClient.getUnitsLoaded(limit) match {
        case Some(units) =>
          AtmosphereClient.broadcast("/unit", JsonMessage(toJson("loaded", units.getValue.getUnits.asScala)))
        case None =>
      }
  }

  def toJson(action: String, unit: DRIUnit) : JObject = toJson(action, List(unit))

  def toJson(action: String, units: List[DRIUnit]) : JObject = (action ->
      ("unit" ->
        units.map(_.toJson())
      )
    )

  def toJson(action: String, unitUid: DRIUnit.UnitUID) = (action ->
      ("unit" ->
        ("uid" -> unitUid)
      )
    )

  def toJson(action:String, unitUid: DRIUnit.UnitUID, progressPercentage: Integer) = (action ->
    ("uid" -> unitUid) ~
      ("percentage" -> progressPercentage.toString)
    )

  def toJson(action: String, unitUid: DRIUnit.UnitUID, label: DRIUnit.Label, errorMessage: String) = (action ->
      ("uid" -> unitUid) ~
      ("label" -> label) ~
      ("message" -> errorMessage)
    )

  def toJson(action: String, units: mutable.Buffer[DriUnitType]) = (action ->
    ("unit" ->
       units.map {
         unit =>
           ("label" -> unit.getLabel) ~
           ("medium" -> getMediaLabel(unit.getMedium)) ~
           ("loaded" -> unit.getLoaded.toGregorianCalendar.getTimeInMillis)
       }
      )
    )

  def getMediaLabel(media: MediaType): String = {
    media match {
      case MediaType.FILE_SYSTEM_FOLDER => "File system folder"
      case MediaType.FLOPPY_DISK => "Floppy disk"
      case MediaType.HARD_DRIVE => "Hard drive"
      case MediaType.PGPZ_FILE => "PGPZ file"
      case MediaType.PORTABLE_NVRAM => "Portable NVRAM"
      case MediaType.TAPE => "Tape"
      case MediaType.TAR_FILE => "TAR file"
      case MediaType.TRUE_CRYPT_VOLUME_FILE => "TrueCrypt volume file"
      case _ => media.value
    }
  }

  private def getDestString(p:TargetedPart): String = {
    var destString = ""
    val dest = Destination.invert(p.destination)
    for (destLocation <- dest) {
      destString = destString + destLocation + "_"
    }
    //remove "_" from the end
    if (destString != "")
      destString = destString.substring(0, destString.length-1)
    return destString
  }

}