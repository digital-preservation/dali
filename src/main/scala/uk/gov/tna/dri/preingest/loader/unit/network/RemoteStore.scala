package uk.gov.tna.dri.preingest.loader.unit.network

import fr.janalyse.ssh.{SSH, SSHOptions}
import scala.Some
import fr.janalyse.ssh.SSHOptions
import java.text.SimpleDateFormat
import scala.collection.mutable.ListBuffer
import grizzled.slf4j.Logging


object RemoteStore extends Logging {

  def createOpt(host:String, user:String, sshPrivateFile:String, sshTimeout:Long) :SSHOptions =  {
    return SSHOptions(host, username = user, sshKeyFile = Some(sshPrivateFile), timeout = sshTimeout)
  }

  def listFiles(opt:SSHOptions, path:String, extension:String): List[RemotePath] =  {
    val ls = SSH.shell(opt) {
      sh=>
        //todo ld: move to ftp ls command
        val lsCommand = "ls --time-style='+%d-%m-%Y,%H:%M:%S' " + path + "/" + extension + " -l | awk ' { print $5, $6, $7  } '"

        sh.executeAndTrimSplit(lsCommand)
    }
    parselsResult(ls)
  }

  //extracts the list command results
  private def parselsResult(files: Iterable[String]): List[RemotePath] = {

    var pathListBuffer = new ListBuffer[RemotePath]()
    //e.g. 1348 30-05-2014,14:12:31 /dri-upload/parts.zip.gpg
    val TCListItemExtractor = """([0-9]+)\s([0-9]{2}-[0-9]{2}-[0-9]{4},[0-9]{2}:[0-9]{2}:[0-9]{2})\s(.+)""".r
    val dateFormat = new SimpleDateFormat("dd-MM-yyyy,kk:mm:ss")
    var i = 0
    try {
      files.foreach(lfile => {
        val o: String = lfile
        o match {
          case TCListItemExtractor(fileSize, dateString, name) =>
            val d = dateFormat.parse(dateString)
            val longMillis = d.getTime
            val rp = new RemotePath(name, fileSize.toLong, longMillis)
            pathListBuffer += rp
         case _ =>
        }
      }
    )
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
