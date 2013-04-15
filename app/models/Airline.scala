package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
//import play.api.libs.json.Format._
import play.api.libs.functional.syntax._

import play.api.Play.current

import play.api.Logger

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import org.joda.time.{DateTime => JodaDateTime}

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import transformers._


case class Airline(
  id: Option[BSONObjectID],
  icao: String, 
  creationDate: Option[JodaDateTime]) {

  def withInfo: Future[AirlineInfo] = {
    Logger.debug("Getting AirlineInfo for " + this.icao)
    // Get it from Mongo...
    Airline.info(this).filter(_.isDefined).recoverWith[Option[AirlineInfo]] {
      // ...or get it from FlightAware
      case e:NoSuchElementException => {
        Logger.info("Airline info for ${airline.icao} being requested from FA and cached")
        val futureAirlineInfo = FlightAware.airlineInfo(this)
        futureAirlineInfo map (Airline.insertAirlineInfo(_)) // insert into mongo
        futureAirlineInfo map (Some(_)) // return it
      }
    } collect {
      case Some(airlineInfo) => airlineInfo
    }
  }

}

case class AirlineInfo(
  id: Option[BSONObjectID],
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
  val airlineReads = Json.reads[Airline]
  val airlineWrites = Json.writes[Airline]
  implicit val airlineFormat = Format[Airline](airlineReads, airlineWrites)
  implicit val airlineInfoFormat = Json.format[AirlineInfo]

  /* Mongo Collections */   
  val db = ReactiveMongoPlugin.db
  def airlines: JSONCollection = db.collection[JSONCollection]("airlines")
  def airlineInfos: JSONCollection = db.collection[JSONCollection]("airlineInfos")

  /* DAO Methods */
  def all: Future[List[Airline]] = {
    airlines.find(Json.obj()).cursor[Airline].toList
  }

  def findByICAO(icao: String): Future[Option[Airline]] = {
    airlines.find(Json.obj("icao" -> icao)).cursor[Airline].headOption
  }

  def info(airline: Airline): Future[Option[AirlineInfo]] = {
    airlineInfos.find(Json.obj("icao" -> airline.icao)).cursor[AirlineInfo].headOption 
  }

  def insertAirlineInfo(airlineInfo: AirlineInfo) = {
    Logger.info(s"Inserting ${airlineInfo.icao} to mongo")
    airlineInfos.insert(airlineInfo)
  }

  def importJson = {
    Logger.info("Importing airline json")
    import play.api.libs.json._
    val data = io.Source.fromFile("AllAirlines").mkString("");

    val json = Json.parse(data)

    val airlineCodes = (json \ "AllAirlinesResult" \ "data").as[List[String]]

    airlineCodes.map(a => Airline(None, a, Some(JodaDateTime.now())))
                .foreach(a => airlines.insert(a).map( le =>
                  le.errMsg match {
                    case Some(errMsg) => Logger.error(errMsg)
                    case None => Logger.info("Added airline to mongo")
                  }
                ))
  }

}

object AirlineInfo {


}
