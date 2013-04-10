package models

import play.api.libs.json._
import play.api.mvc.PathBindable

import scala.concurrent.Await
import scala.concurrent.duration._

import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.Airlines

case class Airline(icao: String, 
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
object Airline {

  implicit val airlineFormat = Json.format[Airline]

  implicit class WithInfo(airline: Airline) {
    def withInfo: Future[AirlineInfo] = {
      // Get it from Mongo...
      Airlines.info(airline).filter(_.isDefined).recoverWith[Option[AirlineInfo]] {
        case e:NoSuchElementException => FlightAware.airlineInfo(airline).map(Some(_))
      } collect {
        case Some(airlineInfo) => airlineInfo
      }
    }
  }

  implicit def pathBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[Airline] {
      
    def bind(key: String, value: String): Either[String, Airline] = 
      // unfortunately we need to block as PathBinders must be synchronous
      for {
        // bind the string
        icao <- stringBinder.bind(key, value).right
        airline <- Await.result(Airlines.findByICAO(icao), 1 second).toRight("Future failed").right
      } yield airline

    def unbind(key: String, airline: Airline): String = 
      stringBinder.unbind(key, airline.icao)

  }  

}

object AirlineInfo {

  implicit val airlineInfoFormat = Json.format[AirlineInfo]

}
