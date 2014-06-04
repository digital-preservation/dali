package uk.gov.tna.dri.preingest.loader.unit.network

import uk.gov.tna.dri.preingest.loader.Settings
import fr.janalyse.ssh.SSHOptions

//global variable
object GlobalUtil {

  //protected val settings = Settings(context.system)

  var processing = false

  def initProcessing(opts:SSHOptions, loadFile: String){
    var processing = true
    RemoteStore.createFile(opts, loadFile)
  }

  def cleanupProcessing(opts:SSHOptions, cleanupFile: String){
    var processing = false
    //todo laura remove loading file
    RemoteStore.deleteFile(opts, cleanupFile)

  }
}
