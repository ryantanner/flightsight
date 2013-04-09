package models

import play.api.db._
import play.api.Play.current
import play.api.mvc.PathBindable
import play.api.libs.json._
import play.api.data.validation.ValidationError

import scala.slick.driver.H2Driver.simple._

case class Airport(icao: String, iata: String, name: String, city: String, country: String,
                   latDeg: Int, latMin: Int, latSec: Int, latDir: String, longDeg: Int,
                   longMin: Int, longSec: Int, longDir: String, altitude: Int)

object Airport {
  
  def database = Database.forDataSource(DB.getDataSource())

  val AirportTable = new Table[Airport]("airport") {
    def icao = column[String]("icao", O.PrimaryKey)
    def iata = column[String]("iata")
    def name = column[String]("name")
    def city = column[String]("city")
    def country = column[String]("country")
    def latDeg = column[Int]("latDeg")
    def latMin = column[Int]("latMin")
    def latSec = column[Int]("latSec")
    def latDir = column[String]("latDir")
    def longDeg = column[Int]("longDeg")
    def longMin = column[Int]("longMin")
    def longSec = column[Int]("longSec")
    def longDir = column[String]("longDir")
    def altitude = column[Int]("altitude")
    def * = icao ~ iata ~ name ~ city ~ country ~ latDeg ~ latMin ~ latSec ~ 
            latDir ~ longDeg ~ longMin ~ longSec ~ longDir ~ altitude <> (Airport.apply _, Airport.unapply _)

  }

  def findByICAO(icao: String): Option[Airport] = database.withSession { implicit db: Session =>
    Query(AirportTable).filter(a => a.icao === icao).firstOption
  }

  def findByIATA(iata: String): Option[Airport] = database.withSession { implicit db: Session =>
    Query(AirportTable).filter(a => a.iata === iata).firstOption
  }

  implicit def pathBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[Airport] {

    def bind(key: String, value: String): Either[String, Airport] =
      for {
        iata <- stringBinder.bind(key, value).right
        airport <- Airport.findByIATA(iata).toRight("Airport not found").right
      } yield airport

    def unbind(key: String, airport: Airport): String =
      stringBinder.unbind(key, airport.iata)

  }

  /*
  def airportReads(implicit r: Reads[String]): Reads[Airport] = {
    r.collect(ValidationError("Could not deserialize airport")) { 
      case airportCode:String => findByICAO(airportCode) flatMap {
        case Some(airport) => airport
      }
    }
  }
  */

  implicit object AirportReads extends Reads[Airport] {
    def reads(json: JsValue) = json match {
      case JsString(airportCode) => findByICAO(airportCode) match {
        case Some(airport) => JsSuccess(airport)
        case None => JsError(Seq(JsPath() -> Seq(ValidationError("Could not convert to airport"))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("Could not convert to airport"))))
    }
  }


}

