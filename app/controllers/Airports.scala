package controllers

import play.api.mvc._
import play.api.Play.current
import play.api.mvc.PathBindable
import play.api.libs.json._
import play.api.data.validation.ValidationError

import play.api.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import org.joda.time.{DateTimeZone, DateTime => JodaDateTime}

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._

import models._
import models.transformers._


object Airports extends Controller with MongoController {

  //val db = ReactiveMongoPlugin.db
  def airports: JSONCollection = db.collection[JSONCollection]("airports")
  def airportInfos: JSONCollection = db.collection[JSONCollection]("airportInfos")

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



}

