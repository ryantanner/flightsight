package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json.Format._
import play.api.libs.functional.syntax._

import play.api.Logger

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.joda.time.{DateTime => JodaDateTime}

import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

import controllers.Airlines
import transformers._


case class Airline(id: Option[BSONObjectID],
                   icao: String, 
                   creationDate: Option[JodaDateTime])

case class AirlineInfo(id: Option[BSONObjectID],
                       icao: String,
                       callsign: String,
                       country: String,
                       location: String,
                       name: String,
                       phone: String,
                       shortname: String,
                       url: String,
                       creationDate: Option[JodaDateTime])
object Airline {

  /* JSON Formatters and Transformers */
  implicit val airlineFormat = Json.format[Airline]


  implicit class WithInfo(airline: Airline) {
    def withInfo: Future[AirlineInfo] = {
      Logger.debug("Getting AirlineInfo for " + airline.icao)
      // Get it from Mongo...
      Airlines.info(airline).filter(_.isDefined).recoverWith[Option[AirlineInfo]] {
        // ...or get it from FlightAware
        case e:NoSuchElementException => 
          val futureAirlineInfo = FlightAware.airlineInfo(airline)
          futureAirlineInfo map (Airlines.insertAirlineInfo(_)) // insert into mongo
          futureAirlineInfo map (Some(_)) // return it
      } collect {
        case Some(airlineInfo) => airlineInfo
      }
    }
  }



}

object AirlineInfo {

  implicit val airlineInfoFormat = Json.format[AirlineInfo]

}
