package uk.gov.tna.dri.jassh

import org.specs2.mutable.Specification
import fr.janalyse.ssh._
import scalax.file._

/**
 * Created by dev on 5/8/14.
 */


class TestScala extends Specification {
  "simpleTest" should {
    "add two numbers" in {
      1 + 1 mustEqual 2
    }
    "add three numbers" in {
      1 + 1 + 1 mustEqual 3
    }
  }

//IMPORTANT - to test with localhost, the public key should be in the authorized_keys
//cp ~/.ssh/id_rsa.pub ~/.ssh/authorized_keys
  "sshTest" should {
    import util.Properties.{userName => user}
    val sshPrivateFile = "/home/dev/.ssh/id_rsa"
    val opts = SSHOptions("127.0.0.1", username = user, sshKeyFile = Some(sshPrivateFile), timeout = 10000)

    "test my name " in {
       SSH.shell(opts) {
         sh =>
           val name = sh.whoami
           name mustEqual "dev"
       }
     }

//create a file
    "create and verify file" in {
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
          fileExists mustEqual true
      }
     }

//list files
    "list files" in {
      SSH.shell(opts) {
        sh =>
          val dir = "/home/dev/test"
          val ls = sh.execute("ls "+ dir).trim()
          ls mustEqual "loading"
      }
    }

//get a file
     "get file" in {
       SSH.ftp(opts) {
        ftp=>
          val remoteFile = "/home/dev/test/loading"
          val localFile = "/home/dev/test/loading2"
          ftp.receive(remoteFile, localFile)
          val file = new java.io.File(localFile)
          file.exists mustEqual true
       }
     }

    //delete file
    "delete files" in {
      SSH.shell(opts) {
        sh =>
          val testedfile = "/home/dev/test/loading2"
          Path.fromString(testedfile).delete()
          val fileExists = sh.exists(testedfile)
          fileExists mustEqual false
      }
    }

 }
}

