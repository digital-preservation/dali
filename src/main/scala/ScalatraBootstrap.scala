import akka.actor.{Props, ActorSystem}
import org.scalatra.LifeCycle
import javax.servlet.ServletContext
import uk.gov.tna.dri.preingest.loader.auth.RememberMeDb
import uk.gov.tna.dri.preingest.loader.certificate.CertificateManagerActor
import uk.gov.tna.dri.preingest.loader.{PreIngestLoaderActor, PreIngestLoader}
import uk.gov.tna.dri.preingest.loader.unit.{UnitManagerActor, PendingUnitsActor}

class ScalatraBootstrap extends LifeCycle {

  val PREINGEST_LOADER_ACTOR_SYSTEM = "dri-preingest-loader-actorSystem"

  override def init(context: ServletContext) {

    val system = ActorSystem("dri-preingest-loader")
    lazy val preIngestLoaderActor = system.actorOf(Props[PreIngestLoaderActor], name="PreIngestLoaderActor")
    lazy val certificateManagerActor = system.actorOf(Props[CertificateManagerActor], name="certificateManagerActor")

    context.setAttribute(PREINGEST_LOADER_ACTOR_SYSTEM, system)

    context mount (new PreIngestLoader(system, preIngestLoaderActor, certificateManagerActor), "/*")
  }
  override def destroy(context: ServletContext) {
    super.destroy(context)
    context.getAttribute(PREINGEST_LOADER_ACTOR_SYSTEM).asInstanceOf[ActorSystem].shutdown()
    RememberMeDb.close
  }
}