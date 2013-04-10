package models

import play.api.Play.current
import play.api.mvc.PathBindable
import play.api.libs.json.Json
import play.api.data._
import play.api.Logger

import scala.concurrent.Future

import org.joda.time.{DateTime => JodaDateTime}

import reactivemongo.api._
import play.modules.reactivemongo._


case class Airline(icao: String, 
                   info: Option[AirlineInfo],
                   creationDate: Option[JodaDateTime])

case class AirlineInfo(icao: String,
                       callsign: String,
                       country: String,
                       location: String,
                       name: String,
                       phone: String,
                       shortname: String,
                       url: String,
                       creationDate: Option[JodaDateTime])

object Airline extends Mongo {
  
  implicit val airlineFormat = Json.format[Airline]
  implicit val airlineInfoFormat = Json.format[AirlineInfo]

  def findByICAO(icao: String): Future[Option[Airline]] = {
    airlineColl.find(Json.obj("icao" -> icao)).headOption
  }

  def all: Future[List[Airline]] = {
    airlineColl.toList
  }

  implicit def pathBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[Airline] {
    
    def bind(key: String, value: String): Either[String, Airline] = 
      for {
        icao <- stringBinder.bind(key, value).right
        airlineF <- findByICAO(icao).toRight("Future failed").right
        airline  <- airlineF.toRight("Airline not found").right
      } yield airline

    def unbind(key: String, airline: Airline): String = 
      stringBinder.unbind(key, airline.code)

  }  

  def importJson = {
    import play.api.libs.json._
    val data = io.Source.fromFile("AllAirlines").mkString("");
    val json = Json.parse(data)
    val airlineCodes = json \ "AllAirlinesResults" \ "data"

    val airlines = airlineCodes map { c =>
      val obj = Json.obj(
        "icao" -> c,
        "info" -> None,
        "creationDate" -> JodaDateTime.now()
      )
      obj.as[Airline]
    }

    airlines.foreach(a => airlinesColl.insert(a))
  }
    


}
