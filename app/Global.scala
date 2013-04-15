import play.api._

import scala.concurrent.ExecutionContext.Implicits.global

import controllers._
import models._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")

    // Load airline ICAO codes from file
    val futureAirlines = Airlines.all

    futureAirlines map { airlines =>
      if(airlines.isEmpty) {
        Airlines.importJson
      }
    }

    // Load airport ICAO codes from file
    val futureAirports = Airports.all

    futureAirports map { airports =>
      if(airports.isEmpty) {
        Airports.importJson
      }
    }
  }  
  
}
