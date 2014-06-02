package uk.gov.tna.dri.jassh

import org.json4s._
import org.json4s.jackson.JsonMethods._

import org.specs2.mutable.Specification
import fr.janalyse.ssh._
import scalax.file._
import uk.gov.tna.dri.preingest.loader.util.RemotePath
import java.text.SimpleDateFormat
import scala.collection.mutable.ListBuffer
import uk.gov.tna.dri.preingest.loader.Settings
import uk.gov.tna.dri.preingest.loader.unit.network.RemoteStore

/**
 * Created by dev on 5/8/14.
 */


class TestScala extends Specification {

  //private val settings = Settings(context.system)
  "parse" should {
    implicit val formats = DefaultFormats
    case class Child(name: String, age: Int, birthdate: Option[java.util.Date])
    case class Address(street: String, city: String)
    case class Person(name: String, address: Address, children: List[Child])
    val json = parse("""
         { "name": "joe",
           "address": {
             "street": "Bulevard",
             "city": "Helsinki"
           },
           "children": [
             {
               "name": "Mary",
               "age": 5,
               "birthdate": "2004-09-04T18:06:22Z"
             },
             {
               "name": "Mazy",
               "age": 3
             }
           ]
         }
                            """)

    val Person2 = json.extract[Person]
    println(Person2)
    "add two numbers" in {
      1 + 1 mustEqual 2
    }

  }



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
          val ls = sh.execute("ls --time-style='+%d-%m-%Y,%H:%M:%S'  -l "+ dir + " | awk ' { print $5, $6, $7  } '" ).trim()
          // I must convet this structure
          //0 May 13 14:05 loading
          //11 May 12 17:09 test2'
          //to a list of RemotePath
          val tokens =  ls split ("""\s+""") toList
          val f = new SimpleDateFormat("dd-MM-yyyy,kk:mm:ss")
          var pathListBuffer = new ListBuffer[RemotePath]()

          var i = 0
          for (i <- 0 until tokens.size/3) {
            val j = i*3
            val filesize = tokens(j) toLong
            val dateString = tokens(j+1)

            val d = f.parse(dateString)
            val longMillis = d.getTime

            val name = tokens(j+2)

            //info("file name " + name + " size " + filesize + "date " + longMillis)

            val rp = new RemotePath(name, filesize, longMillis)
            pathListBuffer +=  rp
          }



           val pathList = List( new RemotePath("/dri-upload/dummmy.gpgz", 0, 1399999489000L), new RemotePath("/dri-upload/a.gpgz", 11, 1399910957000L))
           val pathListLoading = List( new RemotePath("/dri-upload/a.gpgz", 0, 1399999489000L))

           val plf =  pathList.filterNot(uu => pathListLoading.exists(_.name.equals(uu.name))).toList


            "loading" mustEqual "loading"

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


    //scp a file
    "scp  file" in {

      val scpOb = new SSH(opts)

      scpOb.scp {
        scp=>
          val remoteFile = "/home/dev/test/loading"
          val localFile = "/tmp/loading/" + remoteFile.substring(remoteFile.lastIndexOf("/")+1)
          scp.receive(remoteFile, localFile)
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

