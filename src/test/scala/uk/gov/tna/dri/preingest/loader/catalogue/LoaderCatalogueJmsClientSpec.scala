package uk.gov.tna.dri.preingest.loader.catalogue

import org.specs2.mutable.Specification
import uk.gov.nationalarchives.dri.ingest.PartIdType
import org.specs2.mock.Mockito
import uk.gov.tna.dri.catalogue.jms.client.JmsConfig

/**
 * Created with IntelliJ IDEA.
 * User: Rob Walpole
 * Date: 5/21/14
 * Time: 10:44 AM
 */
class LoaderCatalogueJmsClientSpec extends Specification with Mockito {
  //args(skipAll = true)
  val partId = new PartIdType
  //val mockJmsConfig = mock[JmsConfig]
  val jmsConfig = new JmsConfig ("tcp://localhost:61616", "admin", "admin" , "dri.catalogue" , 60000)
  val catalogueJmsClient = new LoaderCatalogueJmsClient(jmsConfig)

  "The getUnitsLoaded method" should {
    "return 2 elements" in {
      catalogueJmsClient.getUnitsLoaded(10).get.getValue.getUnits must have size(2)
    }
  }




}
