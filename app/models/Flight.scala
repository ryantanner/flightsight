package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
//import play.api.libs.json.Writes._
//import play.api.libs.json.Format._
import play.api.libs.functional.syntax._
import play.api.libs.iteratee._

import play.api.data.validation.ValidationError
import play.api.mvc.PathBindable
import play.api.Play.current

import play.api.libs.concurrent.Akka

import play.api.Logger

import org.joda.time.{DateTimeZone => JodaTimeZone, DateTime => JodaDateTime}
import org.joda.time.Period

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout

import reactivemongo.api._
import reactivemongo.bson._

import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import DateTimeZone._
import DateTime._

import transformers._
import POI.geoPointFormat
import actors._

case class Flight(
  id: BSONObjectID,
  faFlightId: String, 
  actualArrivalTime: Option[JodaDateTime], 
  actualDepartureTime: Option[JodaDateTime], 
  aircraftType: String,
  destination: String, 
  destinationCity: String, 
  destinationName: String, 
  estimatedArrivalTime: JodaDateTime, 
  filedAirspeedKts: Int, 
  filedAltitude: Int, 
  filedDepartureTime: JodaDateTime, 
  filedETE: Period, 
  filedTime: JodaDateTime,
  ident: String, 
  origin: String, 
  originCity: String, 
  originName: String, 
  creationDate: JodaDateTime) {

  def number: Int = ident.filter(_.isDigit).toInt

  def withAirportInfo: Future[FlightAirportInfo] = {
    Airport.findByICAO(origin) flatMap { maybeOrigin =>
      maybeOrigin map { origin =>
        Airport.findByICAO(destination) flatMap { maybeDestination =>
          maybeDestination map { destination =>
            for {
              originInfo      <- origin.withInfo
              destinationInfo <- destination.withInfo
            } yield FlightAirportInfo(originInfo, destinationInfo)
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

case class FlightAirportInfo(origin: AirportInfo, destination: AirportInfo)

object Flight {

  /* Mongo Collections */
  val db = ReactiveMongoPlugin.db
  val flightsColl: JSONCollection = db.collection[JSONCollection]("flights")

  implicit val flightReads = Json.reads[Flight]
  implicit val flightWrites = Json.writes[Flight]

  /* JSON Transformers */
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
    (__ \ "creationDate").read[JodaDateTime]
  )(Flight.apply _)

  lazy val routes = Akka.system.actorOf(Props[Routes])
  implicit val timeout = Timeout(1 second)

  def all: Future[List[Flight]] = {
    flightsColl.find(Json.obj()).cursor[Flight].toList
  }

  def findByFlightNumber(airline: Airline, flightNumber: Int, departureTime: JodaDateTime): Future[List[Flight]] = {
    // Get it from mongo...
    val startDate = departureTime.withTime(0, 0, 0, 0)
    val endDate = departureTime.withTime(23, 59, 59, 999)

    flightsColl.find(Json.obj(
      "ident" -> JsString(airline.icao + flightNumber),
      "filedDepartureTime" -> Json.obj(
        "$gte" -> startDate.getMillis(),
        "$lt"  -> endDate.getMillis()
      )
    )).cursor[Flight].toList flatMap { flights => 
      Logger.debug(s"Found ${flights.length} matching flights in mongo")

      flights match {
        case head :: tail => Future.successful(head :: tail)
                             // .. or try FlightAware
        case Nil          => FlightAware.findByFlightNumber(airline, flightNumber, departureTime)
      }
    }
  }

  def findByNumberOriginDestination(airline: Airline, flightNumber: Int, origin: Airport, destination: Airport, departureTime: JodaDateTime): Future[Option[Flight]] = {
    val startDate = departureTime.withTime(0, 0, 0, 0)
    val endDate = departureTime.withTime(23, 59, 59, 999)

    flightsColl.find(Json.obj(
      "ident" -> JsString(airline.icao + flightNumber),
      "filedDepartureTime" -> Json.obj(
        "$gte" -> startDate.getMillis(),
        "$lt"  -> endDate.getMillis()
      ),
      "destination" -> destination.icao,
      "origin" -> origin.icao
    )).cursor[Flight].headOption flatMap { maybeFlight => maybeFlight match {
      case Some(flight) => Future.successful(Some(flight))
      case None         => FlightAware.findByFlightNumber(airline, flightNumber, departureTime, Some((origin, destination))).map(_.headOption)
    }}
  }

  def findByFaFlightId(faFlightId: String): Future[Option[Flight]] = {
    flightsColl.find(Json.obj(
      "faFlightId" -> faFlightId
    )).cursor[Flight].headOption flatMap { maybeFlight => maybeFlight match {
      case Some(flight) => Future.successful(Some(flight))
      case None         => FlightAware.findByFaFlightId(faFlightId)
    }}
  }

  def route(faFlightId: String): Future[Option[Enumerator[FlightPoint]]] = {
    Logger.debug(s"Determining route type for $faFlightId")
    Flight.findByFaFlightId(faFlightId) map { maybeFlight => maybeFlight map { flight =>
      (flight.actualDepartureTime, flight.actualArrivalTime) match {
        case (None, None) => Enumerator.flatten(retrievePlannedRoute(flight))
        case (Some(departureTime), None) => Enumerator.flatten(retrieveLiveRoute(flight))
        case (Some(departureTime), Some(arrivalTime)) => Enumerator.flatten(retrievePastRoute(flight))
        case (None, Some(arrivalTime)) => Enumerator.eof // tear in fabric of space in time, bail out
      }
    }}
  }

  def retrievePlannedRoute(flight: Flight): Future[Enumerator[FlightPoint]] = {
    Logger.debug(s"Retrieving planned route for ${flight.ident}")
    FlightAware.plannedRoute(flight) map { route =>
      Enumerator.enumerate(route)
    }
  }

  def retrieveLiveRoute(flight: Flight): Future[Enumerator[FlightPoint]] = {
    // Get known live data, then hoist a callback to poll for new
    // updates periodically
    Logger.debug(s"Retrieving live route for ${flight.ident}")

    Akka.system.scheduler.schedule(10 seconds, 1 minutes) {
      FlightAware.lastTrack(flight) onSuccess { case points: Seq[FlightPoint] =>
        routes ! Route(flight, points)
      }
    }
    
    (routes ? Track(flight)) map {
      case Connected(enumerator) =>
        enumerator
    }

  }

  def retrievePastRoute(flight: Flight): Future[Enumerator[FlightPoint]] = {
    Logger.debug(s"Retrieving completed route for ${flight.ident}")
    FlightPoint.points.find(Json.obj(
      "$orderby" -> Json.obj(
        "timestamp" -> 1
      ),
      "$query" -> Json.obj(
        "faFlightId" -> flight.faFlightId,
        "planned"    -> false
      )
    )).cursor[FlightPoint].toList flatMap { points =>
      Logger.debug(s"Found ${points.size} points for ${flight.ident}")

      val reload = (for {
        arrivalTime   <- flight.actualArrivalTime
        lastPoint     <- points.lastOption
        lastPointTime <- lastPoint.timestamp
      } yield arrivalTime.isAfter(lastPointTime)) getOrElse true

      if(reload || points.isEmpty) {
        Logger.debug(s"Reloading points for ${flight.ident}")
        val newPoints = FlightAware.historicTrack(flight) map { track =>
          track foreach FlightPoint.insert
          Enumerator.enumerate(track)
        }
        newPoints
      }
      else Future.successful(Enumerator.enumerate(points))
    }
  }

  def insert(flight: Flight) = {
    Logger.info(s"Inserting ${flight.faFlightId} to mongo")
    flightsColl.insert(flight) map (le =>
      le.errMsg match {
        case Some(errMsg) => Logger.error(errMsg)
      }
    )
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


