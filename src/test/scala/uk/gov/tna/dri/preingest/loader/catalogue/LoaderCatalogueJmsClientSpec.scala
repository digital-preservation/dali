/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.catalogue

import org.scalatest.{Matchers, FlatSpec}
import org.specs2.mutable.Specification
import uk.gov.nationalarchives.dri.catalogue.api.ingest.PartIdType
import org.specs2.mock.Mockito
import uk.gov.tna.dri.catalogue.jms.client.JmsConfig

/**
 * Created with IntelliJ IDEA.
 * User: Rob Walpole
 * Date: 5/21/14
 * Time: 10:44 AM
 */
//class LoaderCatalogueJmsClientSpec extends Specification with Mockito {
class LoaderCatalogueJmsClientSpec extends FlatSpec with Matchers with Mockito {
  //args(skipAll = true)
  val partId = new PartIdType
  //val mockJmsConfig = mock[JmsConfig]
  val jmsConfig = new JmsConfig ("tcp://localhost:61616", "admin", "admin" , "dri.catalogue" , 60000)
  val catalogueJmsClient = new LoaderCatalogueJmsClient(jmsConfig)

//  "The getUnitsLoaded method" should {
//    "return 2 elements" in {
//      catalogueJmsClient.getUnitsLoaded(10).get.getValue.getUnits must have size(2)
//    }
//  }
  "The getUnitsLoaded method" should "return 2 elements" in {
      catalogueJmsClient.getUnitsLoaded(10).get.getValue.getUnits should have size(2)
    }





}
