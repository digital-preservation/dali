package uk.gov.tna.dri.preingest.loader.unit.network

import scala.util.parsing.combinator.syntactical.StdTokenParsers
import scala.util.parsing.combinator.RegexParsers

/**
 * Created by dev on 5/19/14.
 */
object ExampleLsParserApp extends App {


  val text = "dr-xr-xr-x    5 root     root         1024 Sep 12 16:54 with space.gpgz"
  val text2 ="drwxr-xr-x    2 root     root         4096 Mar 23 2011  mnt.loading"

  //val text = "dr-xr-xr-x    5"

  val result = new ExampleLsParser().apply(text)
  println(result)

  val result2= new ExampleLsParser().apply(text2)
  println(result2)
}


class ExampleLsParser extends RegexParsers {

  def mode = """[d\-](?:[r\-][w\-][x\-Ss]){2}(?:[r\-][w\-][x\-Tt])\+?""".r ^^ {
    case m =>
    m
  }

  def numberOfLinks = "[0-9]+".r ^^ {
    case l =>
      Integer.valueOf(l)
  }

  def uid = """[a-z\-0-9]+""".r ^^ {
    case r =>
    r
  }

  def gid = """[a-z\-0-9]+""".r ^^ {
    case r =>
      r
  }

  def size = "[0-9]+".r ^^ {
    case l =>
      Integer.valueOf(l)
  }

  def date = """(?:Jan|Feb|Mar|Apr|May|Aug|Sep|Oct|Nov|Dec)\s+(?:[0-9]+)\s+(?:(?:[0-9]+:[0-9]+)|[0-9]{4})""".r ^^ {
    case d =>
      d
  }

  def filename = ".+".r ^^ {
    case n =>
      n
  }


  case class FsEntry(mode: String, numberOfLinks: Integer, uid:String, gid:String, size:Int, date: String, name:String)


  def expr = mode ~ numberOfLinks ~ uid ~ gid ~ size ~ date ~ filename  ^^ {
    case m ~ l ~ u ~ g ~ s ~ d ~ f  =>
      FsEntry(m, l, u, g, s, d, f)
  }

  def apply(input: String) : FsEntry  = {
    parseAll(expr, input) match {
      case Success(result, _) =>
        result

      case failure : NoSuccess =>
        scala.sys.error(failure.msg)
    }
  }
}