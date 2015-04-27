/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.unit.network

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
    //  djclipsham 210.tgz.gpg 1174072 1406037709.0000000000 djclipsham/chroot/add_files_here/gpg.tar.gpg
    // need to extract:
    // djclipsham, 210 1174072, 1406037709,  djclipsham/chroot/add_files_here/gpg.tar.gpg
    val fileDetailsExtractor = """^(\w+)\s([^\.]+)\.[^\s]+\s(\d+)\s(\d+)\.\d+\s([^\s]+)\s*$""".r
    files.foreach(fileDetails => {
      fileDetails match {
        case fileDetailsExtractor(username, filename, filesize, timestamp, path)  =>
          pathListBuffer += new RemotePath(username, filename, filesize.toLong, timestamp.toLong, path)
        case _ =>
      }
    })
    pathListBuffer.toList
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


  def deleteFile(opt:SSHOptions, delFile: String) {
    SSH.shell(opt) {
      sh =>
          val filesToDel = List(delFile)
          sh.rm(filesToDel)
      }
  }

}