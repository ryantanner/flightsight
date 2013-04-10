package controllers

import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import play.api.data._
import play.api.Logger

import scala.concurrent.Future

import org.joda.time.{DateTime => JodaDateTime}

import reactivemongo.api._
import play.modules.reactivemongo._

import models._

object Airlines extends Controller with MongoController {

  def airlines: JSONCollection = db.collection[JSONCollection]("airlines")
  def airlineInfos: JSONCollection = db.collection[JSONCollection]("airlineInfos")

  def all = Action {
    val futureAirlines = airlines.toList
    Async {
      futureAirlines.map(al => Ok(al))
    }
  }

  def findByICAO(icao: String): Future[Option[Airline]] = {
    airlines.find(Json.obj("icao" -> icao)).headOption
  }

  def info(airline: Airline): Future[Option[AirlineInfo]] = {
    airlineInfos.find[JsObject, AirlineInfo](Json.obj("icao" -> airline.icao)).headOption 
  }

  def importJson = {
    import play.api.libs.json._
    val data = io.Source.fromFile("AllAirlines").mkString("");
    val json = Json.parse(data)
    val airlineCodes = (json \ "AllAirlinesResults" \ "data").as[List[String]]

    airlineCodes.map(a => Airline(a, Some(JodaDateTime.now())))
                .foreach(a => airlines.insert(a))
  }
    


}
