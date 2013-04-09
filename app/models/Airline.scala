package models

import play.api.db._
import play.api.Play.current
import play.api.mvc.PathBindable

import play.api.Logger

import scala.slick.driver.H2Driver.simple._

case class Airline(code: String, name: String, callsign: String, website: String)

object Airline {
  
  def database = Database.forDataSource(DB.getDataSource())

  val AirlineTable = new Table[Airline]("airline_icao") {
    def code = column[String]("code", O.PrimaryKey)
    def name = column[String]("name")
    def callsign = column[String]("callsign")
    def website = column[String]("website")
    def * = code ~ name ~ callsign ~ website <> (Airline.apply _, Airline.unapply _)
  }
  
  Logger.debug(AirlineTable.ddl.createStatements.mkString("\n"))

  def findByICAO(icao: String): Option[Airline] = database.withSession { implicit db: Session =>
    Query(AirlineTable).filter(a => a.code === icao).firstOption
  }

  implicit def pathBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[Airline] {
    
    def bind(key: String, value: String): Either[String, Airline] = 
      for {
        icao <- stringBinder.bind(key, value).right
        airline <- Airline.findByICAO(icao).toRight("Airline not found").right
      } yield airline

    def unbind(key: String, airline: Airline): String = 
      stringBinder.unbind(key, airline.code)

  }  

}
