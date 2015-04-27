/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader

/**
 * Taken from the Akka docs http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
 */
class PartialFunctionBuilder[A, B] {
  import scala.collection.immutable.Vector

  // Abbreviate to make code fit
  type PF = PartialFunction[A, B]

  private var pfsOption: Option[Vector[PF]] = Some(Vector.empty)

  private def mapPfs[C](f: Vector[PF] ⇒ (Option[Vector[PF]], C)): C = {
    pfsOption.fold(throw new IllegalStateException("Already built"))(f) match {
      case (newPfsOption, result) ⇒ {
        pfsOption = newPfsOption
        result
      }
    }
  }

  def +=(pf: PF): Unit =
    mapPfs { case pfs ⇒ (Some(pfs :+ pf), ()) }

  def result(): PF =
    mapPfs { case pfs ⇒ (None, pfs.foldLeft[PF](Map.empty) { _ orElse _ }) }
}

