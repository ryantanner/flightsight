package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.data.validation.ValidationError
import play.api.mvc.PathBindable

import org.joda.time.{DateTimeZone => JodaTimeZone, DateTime => JodaDateTime}

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.Airports
import DateTimeZone._

case class Airport(icao: String,
                   creationDate: Option[JodaDateTime])

case class AirportInfo(icao: String, 
                       name: String, 
                       location: String, 
                       latitude: String,
                       longitude: String,
                       timezone: JodaTimeZone,
                       creationDate: Option[JodaDateTime])

object Airport {

  implicit val airportReads = Json.reads[Airport]
  implicit val airportWrites = Json.writes[Airport]

  implicit class WithInfo(airport: Airport) {
    def withInfo: Future[AirportInfo] = {
      // Get it from Mongo...
      Airports.info(airport).filter(_.isDefined).recoverWith[Option[AirportInfo]] {
        case e:NoSuchElementException => FlightAware.airportInfo(airport).map(Some(_))
      } collect {
        case Some(airportInfo) => airportInfo
      }
    }
  }

  implicit def pathBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[Airport] {

    def bind(key: String, value: String): Either[String, Airport] =
      // unfortunately we need to block as PathBinders must be synchronous
      for {
        // bind the string
        icao <- stringBinder.bind(key, value).right
        airport <- Await.result(Airports.findByICAO(icao), 1 second).toRight("airport not found").right
        // get a future possible airport
      } yield airport

    def unbind(key: String, airport: Airport): String =
      stringBinder.unbind(key, airport.icao)

  }

  implicit object AirportReads extends Reads[Airport] {
    implicit val path = JsPath()
    def reads(json: JsValue): JsResult[Airport] = json match {
      case JsString(icao) => for {
          maybeAirport <- Await.result(Airports.findByICAO(icao), 1 second).toRight("no airport found").right
        } yield maybeAirport 
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("Could not convert to airport"))))
    }
  }

  implicit def EitherAsJsResult[R](either: Either[String, R])(implicit path: JsPath): JsResult[R] = {
    either match {
      case Left(error) => JsError(Seq(path -> Seq(ValidationError(error))))
      case Right(r) => JsSuccess(r)
    }
  }


}

object AirportInfo {

  implicit val airportInfoFormat:Format[AirportInfo] = Json.format[AirportInfo]

}
