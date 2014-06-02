package uk.gov.tna.dri.preingest.loader.unit.common
import uk.gov.tna.dri.preingest.loader.SettingsImpl

package object unit {

  def isJunkFile(settings: SettingsImpl, name: String) = settings.Unit.junkFiles.find(_.findFirstMatchIn(name).nonEmpty).nonEmpty

}
