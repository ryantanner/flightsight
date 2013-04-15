package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.data.validation.ValidationError
import play.api.Logger

import play.api.Play.current

import org.joda.time.{DateTimeZone => JodaTimeZone, DateTime => JodaDateTime}

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.BSONFormats._
import play.modules.reactivemongo.json.collection._

import DateTimeZone._

import transformers._

case class Airport(
  id: Option[BSONObjectID],
  icao: String,
  creationDate: Option[JodaDateTime]) {

  def withInfo: Future[AirportInfo] = {
    Logger.debug("Getting AirportInfo for " + this.icao)
    // Get it from Mongo...
    Airport.info(this).filter(_.isDefined).recoverWith[Option[AirportInfo]] {
      // ...or get it from FlightAware
      case e:NoSuchElementException => 
        Logger.info("Airport info for ${airport.icao} being requested from FA and cached")
        val futureAirportInfo = FlightAware.airportInfo(this)
        futureAirportInfo map (Airport.insertAirportInfo(_))
        futureAirportInfo map (Some(_))
    } collect {
      case Some(airportInfo) => airportInfo
    }
  }

}

case class AirportInfo(
  id: Option[BSONObjectID],
  icao: String, 
  name: String, 
  location: String, 
  latitude: Double,
  longitude: Double,
  timezone: JodaTimeZone,
  creationDate: Option[JodaDateTime])

object Airport {

  /* JSON Combinators */
  val airportReads = Json.reads[Airport]
  val airportWrites = Json.writes[Airport]
  implicit val airportFormat = Format[Airport](airportReads, airportWrites)

  /* Mongo Collections */
  val db = ReactiveMongoPlugin.db
  def airports: JSONCollection = db.collection[JSONCollection]("airports")
  def airportInfos: JSONCollection = db.collection[JSONCollection]("airportInfos")

  /* DAO Meethods*/
  def all: Future[List[Airport]] = {
    airports.find(Json.obj()).cursor[Airport].toList
  }

  def findByICAO(icao: String): Future[Option[Airport]] = {
    airports.find(Json.obj("icao" -> icao)).cursor[Airport].headOption
  }

  def info(airport: Airport): Future[Option[AirportInfo]] = {
    airportInfos.find(Json.obj("icao" -> airport.icao)).cursor[AirportInfo].headOption 
  }

  def insertAirportInfo(airportInfo: AirportInfo) = {
    Logger.info("Inserting ${airportInfo.icao} info to mongo")
    airportInfos.insert(airportInfo)
  }

  def importJson = {
    import play.api.libs.json._
    val data = io.Source.fromFile("AllAirports").mkString("");
    val json = Json.parse(data)
    val airportCodes = (json \ "AllAirportsResult" \ "data").as[List[String]]

    val validCodes = airportCodes filter (a => a.forall(!_.isDigit))

    validCodes.map(c => Airport(None, c, Some(JodaDateTime.now())))
              .foreach(a => airports.insert(a).map( le =>
                  le.errMsg match {
                    case Some(errMsg) => Logger.error(errMsg)
                    case None => Logger.info("Added airport to mongo")
                  }
               ))
  }

  /*
  implicit def EitherAsJsResult[R](either: Either[String, R])(implicit path: JsPath): JsResult[R] = {
    either match {
      case Left(error) => JsError(Seq(path -> Seq(ValidationError(error))))
      case Right(r) => JsSuccess(r)
    }
  }
  */

}

object AirportInfo {

  implicit val airportInfoFormat:Format[AirportInfo] = Json.format[AirportInfo]

}

