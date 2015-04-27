/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.catalogue

import uk.gov.tna.dri.catalogue.jms.client.{JmsConfig, CatalogueJmsClient}
import javax.xml.bind.JAXBElement
import uk.gov.nationalarchives.dri.catalogue.api.ingest.DriUnitsType
//import akka.actor.Actor
//import uk.gov.nationalarchives.dri.preingest.loader.unit.GetLoaded

/**
 * Created with IntelliJ IDEA.
 * User: Rob Walpole
 * Date: 5/22/14
 * Time: 10:09 AM
 */
class LoaderCatalogueJmsClient(jmsConfig: JmsConfig) extends CatalogueJmsClient(jmsConfig) {

  def getUnitsLoaded(limit: Int): Option[JAXBElement[DriUnitsType]] = {
    logger.info("Retrieving " + limit + " loaded units.")
    val xml = populateXml("getUnitsLoaded", getQuery(10), limitParam)
    val reply = exchangeMessages(xml, jmsConfig)
    reply match {
      case Right(x) => {
        Some(x.getAny.asInstanceOf[JAXBElement[DriUnitsType]])
      }
      case Left(x) => {
        logger.error("Retrieving loaded units from catalogue returned: " + x)
        None
      }
    }
  }
}
