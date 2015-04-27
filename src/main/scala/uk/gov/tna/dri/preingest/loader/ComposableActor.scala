/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader

import akka.actor.Actor

/**
 * Taken from the Akka docs http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
 */
trait ComposableActor extends Actor {
  protected lazy val receiveBuilder = new PartialFunctionBuilder[Any, Unit]
  final def receive = receiveBuilder.result()
}
