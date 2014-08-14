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

  def listFiles(opt:SSHOptions, path: String, extension: String): List[RemotePath] = {
    val found = SSH.shell(opt) {
      sh =>
        // %u=username, %f=filename, %s=filesize, %A@= unix timestamp, %P=full path minus ${path}
        // send 'access denied' errors to /dev/null
        //val findCommand = s"""'find $path -name "$extension" -printf  "%u %f %s %A@ %P"'"""
        val findCommand = s"""find $path -name "$extension" -printf  "%u %f %s %A@ %P\n""""
        sh.executeAndTrimSplit(findCommand)
    }
    parseFindResultFiles(found)
  }

  private def parseFindResultFiles(files: Iterable[String]): List[RemotePath] = {
    var pathListBuffer = new ListBuffer[RemotePath]()
    // expected input to regex has format:
    //  djclipsham gpg.tar.gpg 1174072 1406037709.0000000000 djclipsham/chroot/add_files_here/gpg.tar.gpg
    // need to extract:
    // djclipsham, gpg.tar, 1174072, 1406037709,  djclipsham/chroot/add_files_here/gpg.tar.gpg
    val fileDetailsExtractor = """^(\w+)\s([^\s]+)\.[^\s]+\s(\d+)\s(\d+)\.\d+\s([^\s]+)\s*$""".r
    files.foreach(fileDetails => {
      fileDetails match {
        case fileDetailsExtractor(username, filename, filesize, timestamp, path)  =>
          pathListBuffer += new RemotePath(username + "_" + timestamp + "_"  + filename, username, filename, filesize.toLong, timestamp.toLong, path)
        case _ =>
      }
    })
    pathListBuffer.toList
  }


//  def listFiles(opt:SSHOptions, path:String, extension:String): List[RemotePath] =  {
//    val ls = SSH.shell(opt) {
//      sh=>
//        //todo ld: move to ftp ls command
//        val lsCommand = "ls -R --time-style='+%d-%m-%Y,%H:%M:%S' " + path + "/" + extension + " -l | awk ' { print $5, $6, $7  } '"
//
//        sh.executeAndTrimSplit(lsCommand)
//    }
//    parselsResult(ls)
//  }

  //extracts the list command results
//  private def parselsResult(files: Iterable[String]): List[RemotePath] = {
//
//    var pathListBuffer = new ListBuffer[RemotePath]()
//    //e.g. 1348 30-05-2014,14:12:31 /dri-upload/parts.zip.gpg
//    val TCListItemExtractor = """([0-9]+)\s([0-9]{2}-[0-9]{2}-[0-9]{4},[0-9]{2}:[0-9]{2}:[0-9]{2})\s(.+)""".r
//    val dateFormat = new SimpleDateFormat("dd-MM-yyyy,kk:mm:ss")
//    var i = 0
//    try {
//      files.foreach(file => {
//        val o: String = file
//        o match {
//          case TCListItemExtractor(fileSize, dateString, name) =>
//            val d = dateFormat.parse(dateString)
//            val longMillis = d.getTime
//            var shortName = name.substring(name.lastIndexOf("/")+1, name.indexOf("."))
//            val rp = new RemotePath(shortName, fileSize.toLong, longMillis)
//            pathListBuffer += rp
//         case _ =>
//        }
//      }
//    )
//    }
//    catch {
//      case e: Exception =>
//        warn ("Error parsing " + e.toString)
//    }
//    return pathListBuffer toList
//  }


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


  def deleteFile(opt:SSHOptions, delFile: String) {
    SSH.shell(opt) {
      sh =>
          val filesToDel = List(delFile)
          sh.rm(filesToDel)
      }
  }

}