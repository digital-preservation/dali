/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.tna.dri.jassh

import org.scalatest.{Matchers, FlatSpec}
import org.specs2.mutable.Specification
import org.json4s._
import org.json4s.jackson.JsonMethods._
/**
 * Created by dev on 6/9/14.
*/
object testJSON extends FlatSpec with Matchers{

  implicit val formats = DefaultFormats // Brings in default date formats etc.
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

  val jsonLoad = parse(
    """
      { "actions" : [
      {
        "action": "Load",
        "loadUnit" :
              {
                "uid": "383a118b271b7abff0ebaa62465a8f1cfab9ee20022294dbb58a272896637786",
                "parts" : [
                  {
                   "unit": "/dri-upload/parts2.zip.gpg",
                   "series": "PART1",
                   "destination": "Holding"
                  },
                  {
                   "unit": "/dri-upload/parts2.zip.gpg",
                   "series": "PART2",
                   "destination": "Holding"
                  }
                ]
              },
        "certificate": "myprivate.key",
        "passphrase": "passphrase"
        }
       ]
       }
    """)


  println("json" + jsonLoad)

  import uk.gov.tna.dri.preingest.loader.ClientAction.Actions
  val clientActions = json.extract[Actions]

  println("Client actions" + clientActions)

  val person = json.extract[Person]
  println("person " + person)


  "json parse test"  should "check name is joe" in {
      person.name === "joe"
    }




}
