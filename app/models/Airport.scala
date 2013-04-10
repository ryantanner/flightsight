package models

import play.api.Play.current
import play.api.mvc.PathBindable
import play.api.libs.json._
import play.api.data.validation.ValidationError

import play.api.Logger

import scala.concurrent.Future

import org.joda.time.{DateTimeZone, DateTime => JodaDateTime}

import reactivemongo.api._

import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._

case class Airport(icao: String,
                   info: Option[AirportInfo],
                   creationDate: Option[JodaDateTime])

case class AirportInfo(icao: String, 
                       name: String, 
                       location: String, 
                       latitude: String,
                       longitude: String,
                       timezone: DateTimeZone,
                       creationDate: Option[JodaDateTime])

object Airport extends Mongo {
  
  implicit val airportFormat = Json.format[Airport]
  implicit val airportInfoFormat = Json.format[AirportInfo]

  def findByICAO(icao: String): Future[Option[Airport]] = {
    airportColl.find(Json.obj("icao" -> icao)).headOption
  }

  implicit def pathBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[Airport] {

    def bind(key: String, value: String): Either[String, Airport] =
      for {
        icao <- stringBinder.bind(key, value).right
        airportF <- findByICAO(icao).toRight("Future failed").right
        airport  <- airportF
      } yield airport

    def unbind(key: String, airport: Airport): String =
      stringBinder.unbind(key, airport.icao)

  }

  implicit object AirportReads extends Reads[Airport] {
    def reads(json: JsValue) = json match {
      case JsString(airportCode) => findByICAO(airportCode) match {
        case Some(airport) => JsSuccess(airport)
        case None => JsError(Seq(JsPath() -> Seq(ValidationError("Could not convert to airport"))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("Could not convert to airport"))))
    }
  }

  def importJson = {
    import play.api.libs.json._
    val data = io.Source.fromFile("AllAirports").mkString("");
    val json = Json.parse(data)
    val airportCodes = json \ "AllAirportsResults" \ "data"

    val validCodes = airportCodes filter (a => a.all(!_.isDigit))

    val airports = validCodes map { c =>
      val obj = Json.obj(
        "icao" -> c,
        "info" -> None,
        "creationDate" -> JodaDateTime.now()
      )
      obj.as[Airport]
    }

    airports.foreach(a => airportsColl.insert(a))
  }



}

