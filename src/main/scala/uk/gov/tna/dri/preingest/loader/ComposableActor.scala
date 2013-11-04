package uk.gov.tna.dri.preingest.loader

import akka.actor.Actor

/**
 * Taken from the Akka docs http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
 */
trait ComposableActor extends Actor {
  protected lazy val receiveBuilder = new PartialFunctionBuilder[Any, Unit]
  final def receive = receiveBuilder.result()
}
