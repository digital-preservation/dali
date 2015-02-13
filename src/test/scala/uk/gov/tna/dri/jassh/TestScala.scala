package uk.gov.tna.dri.jassh

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{FlatSpec, Matchers}

import org.specs2.mutable.Specification
import fr.janalyse.ssh._
import scalax.file._
import java.text.SimpleDateFormat
import scala.collection.mutable.ListBuffer
import uk.gov.tna.dri.preingest.loader.Settings
import uk.gov.tna.dri.preingest.loader.unit.network.{RemotePath, RemoteStore}

/**
 * Created by dev on 5/8/14.
 */


class TestScala extends FlatSpec with Matchers {

 //private val settings = Settings(context.system)

  //IMPORTANT - to test with localhost, the public key should be in the authorized_keys
  //cp ~/.ssh/id_rsa.pub ~/.ssh/authorized_keys

    import util.Properties.{userName => user}
    val sshPrivateFile = "id_rsa"
    val opts = SSHOptions("localhost", username = "dev", sshKeyFile = Some(sshPrivateFile), timeout = 10000)
    "sshTest" should "test my name " in {
      SSH.shell(opts) {
        sh =>
          val name = sh.whoami
          name === "dev"
      }
    }

    //create a file
    "sshTest" should "create and verify file" in {
      val content = ""
      val testedfile = "/home/dev/test/loading"

      val scpOb = new SSH(opts)

      scpOb.scp {
        scp=>
          scp.put(content, testedfile)
      }

      SSH.shell(opts) {
        sh =>
          val fileExists = sh.exists(testedfile)
          fileExists === true
      }
    }

    //list files
    "sshTest" should "list files" in {
       val pathList = RemoteStore.listFiles(opts, "/home/dev/gitlab/dri-software/unit-loader/src/test", "*.gpg")

       pathList(0).path === "resources/test.gpg"

    }


    //get a file
    "sshTest" should  "get file" in {
      SSH.ftp(opts) {
        ftp=>
          val remoteFile = "/home/dev/test/loading"
          val localFile = "/home/dev/test/loading2"
          ftp.receive(remoteFile, localFile)
          val file = new java.io.File(localFile)
          file.exists === true
      }
    }


//    //scp a file
//    "scp  file" in {
//
//      val scpOb = new SSH(opts)
//
//      scpOb.scp {
//        scp=>
//          val remoteFile = "/home/dev/test/loading2"
//          val localFile = "/tmp/" + remoteFile.substring(remoteFile.lastIndexOf("/")+1)
//          scp.receive(remoteFile, localFile)
//          val file = new java.io.File(localFile)
//          file.exists === true
//      }
//
//
//    }

    //delete file
  "sshTest" should  "delete files" in {
      SSH.shell(opts) {
        sh =>
          val testedfile = "/home/dev/test/loading2"
          val filesToDel = List(testedfile)
          sh.rm(filesToDel)
          val fileExists = sh.exists(testedfile)
          fileExists === false
      }
    }


}

