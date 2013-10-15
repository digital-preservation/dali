import akka.actor.{Props, ActorSystem}
import org.scalatra.LifeCycle
import javax.servlet.ServletContext
import uk.gov.tna.dri.preingest.loader.auth.RememberMeDb
import uk.gov.tna.dri.preingest.loader.PreIngestLoader
import uk.gov.tna.dri.preingest.loader.unit.PendingUnitsActor

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext) {

    val system = ActorSystem("/dri/preingest/loader")
    val pendingUnitsActor = system.actorOf(Props[PendingUnitsActor])

    context mount (new PreIngestLoader, "/*")
  }
  override def destroy(context: ServletContext) {
    super.destroy(context)
    RememberMeDb.close
  }
}