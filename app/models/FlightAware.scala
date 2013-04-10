package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError
import play.api.libs.ws._
import play.api.Logger

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import org.joda.time.{DateTime => JodaDateTime}
import com.ning.http.client.Realm.AuthScheme

case class Flight(faFlightId: String, actualArrivalTime: Option[JodaDateTime], actualDepartureTime: Option[JodaDateTime], aircraftType: String,
                  destination: Airport, destinationCity: String, destinationName: String, diverted: String,
                  estimatedArrivalTime: JodaDateTime, filedAirspeedKts: Int, filedAirspeedMach: String, 
                  filedAltitude: Int, filedDepartureTime: JodaDateTime, filedETE: String, filedTime: JodaDateTime,
                  ident: String, origin: Airport, originCity: String, originName: String, route: String)

case class ScheduledFlight(actualIdent: String, aircraftType: String, arrivalTime: JodaDateTime, departureTime: JodaDateTime,
                           destination: Airport, ident: String, origin: Airport)

object FlightAware {

  // FlightAware gives us seconds since epoch, we need to x1000 that so it's in milliseconds for Joda
  // If a flight is in the future, actual times are 0 so we'll store a None
  implicit val SecondsOptionalJodaDateReads = new Reads[Option[JodaDateTime]] {

    def reads(json: JsValue): JsResult[Option[JodaDateTime]] = json match {
      case JsNumber(d) if (d > 0) => JsSuccess(Some(new JodaDateTime(d.toLong*1000)))
      case JsNumber(d) if (d == 0) => JsSuccess(None)
      case _           => JsError(Seq(JsPath() -> Seq(ValidationError("expected seconds"))))
    }

  }
 
  // FlightAware gives us seconds since epoch, we need to x1000 that so it's in milliseconds for Joda
  implicit val SecondsJodaDateReads = new Reads[JodaDateTime] {

    def reads(json: JsValue): JsResult[JodaDateTime] = json match {
      case JsNumber(d) => JsSuccess(new JodaDateTime(d.toLong*1000))
      case _           => JsError(Seq(JsPath() -> Seq(ValidationError("expected seconds"))))
    }

  }

  val endpoint = "http://flightxml.flightaware.com/json/FlightXML2/"

  implicit val flightReads = (
    (__ \ "faFlightID").read[String] ~
    (__ \ "actualarrivaltime").read[Option[JodaDateTime]] ~
    (__ \ "actualdeparturetime").read[Option[JodaDateTime]] ~
    (__ \ "aircrafttype").read[String] ~
    (__ \ "destination").read[Airport] ~
    (__ \ "destinationCity").read[String] ~
    (__ \ "destinationName").read[String] ~
    (__ \ "diverted").read[String] ~
    (__ \ "estimatedarrivaltime").read[JodaDateTime] ~
    (__ \ "filed_airspeed_kts").read[Int] ~
    (__ \ "filed_airspeed_mach").read[String] ~
    (__ \ "filed_altitude").read[Int] ~
    (__ \ "filed_departuretime").read[JodaDateTime] ~
    (__ \ "filed_ete").read[String] ~
    (__ \ "filed_time").read[JodaDateTime] ~
    (__ \ "ident").read[String] ~
    (__ \ "origin").read[Airport] ~
    (__ \ "originCity").read[String] ~
    (__ \ "originName").read[String] ~
    (__ \ "route").read[String]
  )(Flight)

  implicit val scheduledFlightReads = (
    (__ \ "actual_ident").read[String] ~
    (__ \ "aircrafttype").read[String] ~
    (__ \ "arrivaltime").read[JodaDateTime] ~
    (__ \ "departuretime").read[JodaDateTime] ~
    (__ \ "destination").read[Airport] ~
    (__ \ "ident").read[String] ~
    (__ \ "origin").read[Airport]
  )(ScheduledFlight)


  def findByFlightNumber(airline: Airline, flightNumber: Int, departureDate: JodaDateTime): Future[List[Flight]] = {
    val params = List(
      ("ident", airline.code + flightNumber),
      ("howMany", "15"))

    def requestFlights(params: List[(String, String)], offset: Int = 0): Future[List[Flight]] = {
      Logger.debug("Requesting flights with offset " + offset);

      val request = wsAuth(endpoint + "FlightInfoEx")
        .withQueryString(params:_*)
        .withQueryString(("offset" -> offset.toString))
        .get

      val futureFlights = request map { response => 
        Logger.debug(response.body)

        val data = response.json \ "FlightInfoExResult"

        (for {
          flights <- ((data \ "flights").validate[List[Flight]].asOpt.toRight("FlightInfoEx call expected JsArray[Flight]")).right
          nextOffset <- ((data \ "next_offset").validate[Int].asOpt.toRight("FlightInfoEx call next_offset not int")).right
        } yield (flights, nextOffset)) fold (
          error => throw new Exception(error),
          success => (success._1, success._2) // unpack (flights, nextOffset) tuple
        )

      }

      futureFlights flatMap { case (flights, nextOffset) =>
        
        flights foreach (f => Logger.debug(f.toString))

        Logger.debug("Flights found: " + flights.length)
        flights match {
          case head :: tail if ((head :: tail).count(f => f.actualDepartureTime.getOrElse(new JodaDateTime()).isBefore(departureDate)) == 0 && nextOffset > 0) =>
            Logger.debug("All flights before given departure, moving to next offset")
            requestFlights(params, nextOffset)
          case head :: tail =>
            Logger.debug("Flights after given departure")
            Logger.debug(departureDate.toLocalDate.toString)
            Logger.debug(head.actualDepartureTime.toString)
            future { (head :: tail).filter(f => f.actualDepartureTime.getOrElse(new JodaDateTime()).toLocalDate.equals(departureDate.toLocalDate)) }
          case Nil => future { Nil }
        }
      } recover {
        case t:Throwable => throw t
      }
          
    }

    requestFlights(params) 
  }

  def findByRoute(airline: Airline, destination: Airport, origin: Airport, departureDate: JodaDateTime): Future[List[ScheduledFlight]] = {
    val params = List[(String,String)](
      ("startDate", departureDate.getMillis.toString), // get epoch of 00:01 of depatureDate
      ("endDate", departureDate.getMillis.toString), // get epoch of 23:59 of departureDate
      ("origin", origin.icao),
      ("destination", destination.icao),
      ("airline", airline.code),
      ("howMany", "15"),
      ("offset", "0"))

    wsAuth(endpoint + "AirlineFlightSchedules")
      .withQueryString(params:_*)
      .get.map { response => 
        Logger.debug(response.body)
        
        val flights = (for {
          flights <- ((response.json \ "data").validate[List[ScheduledFlight]].asOpt.toRight("AirlineFlightSchedules call expected JsArray[ScheduledFlight]")).right
        } yield flights)

        flights fold (
          error => throw new Exception(error),
          flights => flights
        )
      }
  }

  def wsAuth(url: String): WS.WSRequestHolder = {
    WS.url(url).withAuth("ryantanner", "96507582af80a489d35bac3761f91052a2ebc1d1", AuthScheme.BASIC)
  }

}
