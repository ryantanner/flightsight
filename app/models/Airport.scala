package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.data.validation.ValidationError
import play.api.mvc.PathBindable

import play.api.Logger

import org.joda.time.{DateTimeZone => JodaTimeZone, DateTime => JodaDateTime}

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

import controllers.Airports

import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

import DateTimeZone._

import transformers._

case class Airport(id: Option[BSONObjectID],
                   icao: String,
                   creationDate: Option[JodaDateTime])

case class AirportInfo(id: Option[BSONObjectID],
                       icao: String, 
                       name: String, 
                       location: String, 
                       latitude: Double,
                       longitude: Double,
                       timezone: JodaTimeZone,
                       creationDate: Option[JodaDateTime])

object Airport {

  val airportReads = Json.reads[Airport]
  val airportWrites = Json.writes[Airport]
  implicit val airportFormat = Format[Airport](airportReads, airportWrites)

  implicit class WithInfo(airport: Airport) {
    def withInfo: Future[AirportInfo] = {
      // Get it from Mongo...
      Airports.info(airport).filter(_.isDefined).recoverWith[Option[AirportInfo]] {
        case e:NoSuchElementException => 
          val futureAirportInfo = FlightAware.airportInfo(airport)
          futureAirportInfo map (Airports.insertAirportInfo(_))
          futureAirportInfo map (Some(_))
      } collect {
        case Some(airportInfo) => airportInfo
      }
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

