package uk.gov.tna.dri.preingest.loader.unit.disk

import grizzled.slf4j.Logging
import scala.collection.mutable

object NTFS extends Logging {

  val NTFSLABEL_CMD = "/sbin/ntfslabel"

  def getLabel(volume: String) : Option[String] = {
    import scala.sys.process._

    val labelCmd = Seq("sudo", NTFSLABEL_CMD, volume)

    val labelLogger = new ProcessLogger {
      val stdOut = new mutable.ListBuffer[String]
      def out(s: => String) = stdOut += s
      def err(s: => String) = error(s)
      def buffer[T](f: => T) = f
    }

    val resultCode = labelCmd ! labelLogger
    if(resultCode != 0) {
      error(s"Error code '$resultCode' when executing: $labelCmd")
    }

    val label = labelLogger.stdOut.reduceLeft(_.trim + _.trim)
    if(label.isEmpty) {
      None
    } else {
      Option(label)
    }
  }
}
