package uk.gov.tna.dri.preingest.loader.unit.network

import fr.janalyse.ssh.{SSH, SSHOptions}
import scalax.file.Path
import scala.util.Properties._
import scala.Some
import fr.janalyse.ssh.SSHOptions
import uk.gov.tna.dri.preingest.loader.util.RemotePath
import java.text.SimpleDateFormat
import scala.collection.mutable.ListBuffer
import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.SettingsImpl


object RemoteStore extends Logging {

  var processing = false

  def initProcessing(){
    var processing = true
  }

  def cleanupProcessing(){
    var processing = false
  }

  def createOpt(host:String, user:String, sshPrivateFile:String, sshTimeout:Long) :SSHOptions =  {
    return SSHOptions(host, username = user, sshKeyFile = Some(sshPrivateFile), timeout = sshTimeout)
  }

  def listFiles(opt:SSHOptions, path:String, extension:String): List[RemotePath] =  {
    val ls = SSH.shell(opt) {
      sh=>
        //todo ld: move to ftp ls command
        val lsCommand = "ls --time-style='+%d-%m-%Y,%H:%M:%S' " + path + "/" + extension + " -l | awk ' { print $5, $6, $7  } '"
        sh.execute(lsCommand).trim()
    }
    parselsResult(ls)
  }

  private def parselsResult(ls:String): List[RemotePath] = {
    val tokens =  ls split ("""\s+""") toList
    val f = new SimpleDateFormat("dd-MM-yyyy,kk:mm:ss")
    var pathListBuffer = new ListBuffer[RemotePath]()

    var i = 0
    try {
      for (i <- 0 until tokens.size / 3) {
        val j = i * 3
        val fileSize = tokens(j) toLong

        val dateString = tokens(j + 1)
        val d = f.parse(dateString)
        val longMillis = d.getTime

        val name = tokens(j + 2)

        val rp = new RemotePath(name, fileSize, longMillis)
        pathListBuffer += rp
      }
    }
    catch {
      case e: Exception =>
        warn ("Error parsing " + e.toString)
    }

    return pathListBuffer toList
  }


  def createFile(opt:SSHOptions, fileName:String)  {
    val scpOb = new SSH(opt)
    val content = ""

    scpOb.scp {
      scp=>
        scp.put(content, fileName)
    }
  }

  def fileExists(opt:SSHOptions, fileName:String) : Boolean = {
    val fileExists = SSH.shell(opt) {
    sh =>
      sh.exists(fileName)
    }
    return fileExists
  }

  def receiveFile(opt:SSHOptions, remoteFile: String, localFile: String) {
    val scpOb = new SSH(opt)

    scpOb.scp {
      scp=>
        scp.receive(remoteFile, localFile)
    }
  }

}
