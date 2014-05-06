package uk.gov.tna.dri.preingest.loader

package object unit {

  def isJunkFile(settings: SettingsImpl, name: String) = settings.Unit.junkFiles.find(_.findFirstMatchIn(name).nonEmpty).nonEmpty
}
