import org.scalatra.LifeCycle
import javax.servlet.ServletContext
import uk.gov.tna.dri.preingest.loader.auth.RememberMeDb
import uk.gov.tna.dri.preingest.loader.PreIngestLoader

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext) {

    context mount (new PreIngestLoader, "/*")
  }

  override def destroy(context: ServletContext) {
    super.destroy(context)
    RememberMeDb.close
  }
}