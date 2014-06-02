package uk.gov.tna.dri.preingest.loader.unit.network

//global variable
object GlobalUtil {

  var processing = false

  def initProcessing(){
    var processing = true
  }

  def cleanupProcessing(){
    var processing = false
    //todo laura remove loading file
  }
}
