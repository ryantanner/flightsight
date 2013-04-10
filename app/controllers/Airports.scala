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

import reactivemongo.api._

import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._

import models._

object Airports extends Controller with MongoController {

  def airports: JSONCollection = db.collection[JSONCollection]("airports")
  def airportInfos: JSONCollection = db.collection[JSONCollection]("airportInfos")

  def findByICAO(icao: String): Future[Option[Airport]] = {
    airports.find(Json.obj("icao" -> icao)).headOption
  }

  def info(airport: Airport): Future[Option[AirportInfo]] = {
    airportInfos.find[JsObject, AirportInfo](Json.obj("icao" -> airport.icao)).headOption 
  }

  def importJson = {
    import play.api.libs.json._
    val data = io.Source.fromFile("AllAirports").mkString("");
    val json = Json.parse(data)
    val airportCodes = (json \ "AllAirportsResults" \ "data").as[List[String]]

    val validCodes = airportCodes filter (a => a.forall(!_.isDigit))

    validCodes.map(c => Airport(c, Some(JodaDateTime.now())))
              .foreach(a => airports.insert(a))
  }



}

