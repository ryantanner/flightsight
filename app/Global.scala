import play.api._

import scala.concurrent.ExecutionContext.Implicits.global

import controllers._
import models._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")

    // Load airline ICAO codes from file
    val futureAirlines = Airline.all

    futureAirlines map { airlines =>
      if(airlines.isEmpty) {
        Airline.importJson
      }
    }

    // Load airport ICAO codes from file
    val futureAirports = Airport.all

    futureAirports map { airports =>
      if(airports.isEmpty) {
        Airport.importJson
      }
    }
  }  
  
}
