/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
import akka.actor.{Props, ActorSystem}
import org.scalatra.LifeCycle
import javax.servlet.ServletContext
import uk.gov.tna.dri.preingest.loader.auth.RememberMeDb
import uk.gov.tna.dri.preingest.loader.certificate.CertificateManagerActor
import uk.gov.tna.dri.preingest.loader.{PreIngestLoaderActor, PreIngestLoader}

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