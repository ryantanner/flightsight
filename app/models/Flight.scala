package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
//import play.api.libs.json.Writes._
//import play.api.libs.json.Format._
import play.api.libs.functional.syntax._

import play.api.data.validation.ValidationError
import play.api.mvc.PathBindable
import play.api.Play.current

import play.api.Logger

import org.joda.time.{DateTimeZone => JodaTimeZone, DateTime => JodaDateTime}
import org.joda.time.Period

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import reactivemongo.api._
import reactivemongo.bson._

import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import DateTimeZone._
import DateTime._

import transformers._
import controllers._

case class Flight(
  id: BSONObjectID,
  faFlightId: String, 
  actualArrivalTime: Option[JodaDateTime], 
  actualDepartureTime: Option[JodaDateTime], 
  aircraftType: String,
  destination: String, 
  destinationCity: String, 
  destinationName: String, 
  //diverted: String,
  estimatedArrivalTime: JodaDateTime, 
  filedAirspeedKts: Int, 
  //filedAirspeedMach: String, 
  filedAltitude: Int, 
  filedDepartureTime: JodaDateTime, 
  filedETE: Period, 
  filedTime: JodaDateTime,
  ident: String, 
  origin: String, 
  originCity: String, 
  originName: String, 
  creationDate: Option[JodaDateTime]) {

  def number: Int = ident.filter(_.isDigit).toInt

  def withAirportInfo: Future[(Flight, AirportInfo, AirportInfo)] = {
    Airports.findByICAO(origin) flatMap { maybeOrigin =>
      maybeOrigin map { origin =>
        Airports.findByICAO(destination) flatMap { maybeDestination =>
          maybeDestination map { destination =>
            for {
              originInfo      <- origin.withInfo
              destinationInfo <- destination.withInfo
            } yield (this, originInfo, destinationInfo)
          } getOrElse Future.failed(new Exception("no such airport"))
        }
      } getOrElse Future.failed(new Exception("no such airport"))
    }
  }

  def filedArrivalTime: JodaDateTime = {
    // returns in UTC
    filedDepartureTime plus filedETE
  }

}

case class ScheduledFlight(
  id: BSONObjectID,
  actualIdent: String, 
  aircraftType: String, 
  arrivalTime: JodaDateTime, 
  departureTime: JodaDateTime,
  destination: String, 
  ident: String, 
  origin: String,
  creationDate: JodaDateTime) {

  def number: Int = ident.filter(_.isDigit).toInt

}

object Flight {

  /* Mongo Collections */
  val db = ReactiveMongoPlugin.db
  val flights: JSONCollection = db.collection[JSONCollection]("flights")

  implicit val flightReads = Json.reads[Flight]
  /* JSON Transformers */
  /*
  implicit val flightReads: Reads[Flight] = (
    (__ \ "id").read[BSONObjectID] ~
    (__ \ "faFlightID").read[String] ~
    (__ \ "actualarrivaltime").read[Option[JodaDateTime]](DateTime.secondsOptionalJodaDateReads) ~
    (__ \ "actualdeparturetime").read[Option[JodaDateTime]](DateTime.secondsOptionalJodaDateReads) ~
    (__ \ "aircrafttype").read[String] ~
    (__ \ "destination").read[String] ~
    (__ \ "destinationCity").read[String] ~
    (__ \ "destinationName").read[String] ~
    //(__ \ "diverted").read[String] ~
    (__ \ "estimatedarrivaltime").read[JodaDateTime](DateTime.secondsJodaDateReads) ~
    (__ \ "filed_airspeed_kts").read[Int] ~
    //(__ \ "filed_airspeed_mach").read[String] ~
    (__ \ "filed_altitude").read[Int] ~
    (__ \ "filed_departuretime").read[JodaDateTime](DateTime.secondsJodaDateReads) ~
    (__ \ "filed_ete").read[String] ~
    (__ \ "filed_time").read[JodaDateTime](DateTime.secondsJodaDateReads) ~
    (__ \ "ident").read[String] ~
    (__ \ "origin").read[String] ~
    (__ \ "originCity").read[String] ~
    (__ \ "originName").read[String] ~
    (__ \ "creationDate").readNullable[JodaDateTime]
  )(Flight.apply _)
  */

  val faFlightReads: Reads[Flight] = (
    (__ \ "id").read[BSONObjectID] ~
    (__ \ "faFlightID").read[String] ~
    (__ \ "actualarrivaltime").read[Option[JodaDateTime]](DateTime.secondsOptionalJodaDateReads) ~
    (__ \ "actualdeparturetime").read[Option[JodaDateTime]](DateTime.secondsOptionalJodaDateReads) ~
    (__ \ "aircrafttype").read[String] ~
    (__ \ "destination").read[String] ~
    (__ \ "destinationCity").read[String] ~
    (__ \ "destinationName").read[String] ~
    //(__ \ "diverted").read[String] ~
    (__ \ "estimatedarrivaltime").read[JodaDateTime](DateTime.secondsJodaDateReads) ~
    (__ \ "filed_airspeed_kts").read[Int] ~
    //(__ \ "filed_airspeed_mach").read[String] ~
    (__ \ "filed_altitude").read[Int] ~
    (__ \ "filed_departuretime").read[JodaDateTime](DateTime.secondsJodaDateReads) ~
    (__ \ "filed_ete").read[Period] ~
    (__ \ "filed_time").read[JodaDateTime](DateTime.secondsJodaDateReads) ~
    (__ \ "ident").read[String] ~
    (__ \ "origin").read[String] ~
    (__ \ "originCity").read[String] ~
    (__ \ "originName").read[String] ~
    (__ \ "creationDate").readNullable[JodaDateTime]
  )(Flight.apply _)

  def findByFlightNumber(airline: Airline, flightNumber: Int, departureTime: JodaDateTime): Future[List[Flight]] = {
    // Get it from mongo...
    flights.find(Json.obj(
      "ident" -> JsString(airline.icao + flightNumber),
      "departureTime" -> JsNumber(departureTime.getMillis))
    ).cursor[Flight].toList flatMap { flist => 
      Logger.debug(s"Found ${flist.length} matching flights in mongo")
      flist match {
        case head :: tail => Future.successful(head :: tail)
                             // .. or try FlightAware
        case Nil          => FlightAware.findByFlightNumber(airline, flightNumber, departureTime)
      }
    }
  }



}

object ScheduledFlight {

  implicit val scheduledFlightReads: Reads[ScheduledFlight] = (
    (__ \ "id").read[BSONObjectID] ~
    (__ \ "actual_ident").read[String] ~
    (__ \ "aircrafttype").read[String] ~
    (__ \ "arrivaltime").read[JodaDateTime] ~
    (__ \ "departuretime").read[JodaDateTime] ~
    (__ \ "destination").read[String] ~
    (__ \ "ident").read[String] ~
    (__ \ "origin").read[String] ~
    (__ \ "creationDate").read[JodaDateTime]
  )(ScheduledFlight.apply _)

}
