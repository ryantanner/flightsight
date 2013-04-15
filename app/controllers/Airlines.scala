package controllers

import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import play.api.data._
import play.api.Logger

import scala.concurrent.Future

import org.joda.time.{DateTime => JodaDateTime}

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._

import models._
import models.transformers._


object Airlines extends Controller with MongoController {

  //val db = ReactiveMongoPlugin.db
  def airlines: JSONCollection = db.collection[JSONCollection]("airlines")
  def airlineInfos: JSONCollection = db.collection[JSONCollection]("airlineInfos")

  def listAll = Action {
    val futureAirlines = all
    Async {
      futureAirlines.map(al => Ok(Json.arr(al)))
    }
  }

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
