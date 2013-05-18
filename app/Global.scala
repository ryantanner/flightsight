import play.api._

import scala.concurrent.ExecutionContext.Implicits.global

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.api.indexes._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import controllers._
import models._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")

    setIndices

    // Load airline ICAO codes from file
    val futureAirlines = Airline.all

    futureAirlines map { airlines =>
      if(airlines.isEmpty) {
        Airline.importJson
      } else {
        Logger.debug(s"Found ${airlines.length} airlines")
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

  def setIndices = {
    Airline.airlines.indexesManager.ensure(
      Index(List("icao" -> IndexType.Ascending), unique = true))
  
    Airline.airlineInfos.indexesManager.ensure(
      Index(List("icao" -> IndexType.Ascending), unique = true))

    Airport.airports.indexesManager.ensure(
      Index(List("icao" -> IndexType.Ascending), unique = true))

    Airport.airportInfos.indexesManager.ensure(
      Index(List("icao" -> IndexType.Ascending), unique = true))

    /*
    POI.poiColl.indexesManager.ensure(
      Index(List("loc" -> IndexType.Geo2D), unique = true))
      */

    Flight.flightsColl.indexesManager.ensure(
      Index(List("faFlightId" -> IndexType.Ascending), unique = true))

    FlightPoint.points.indexesManager.ensure(
      Index(List("faFlightId" -> IndexType.Ascending,
                 "timestamp"  -> IndexType.Descending,
                 "planned"    -> IndexType.Ascending),
            unique = true)
    )
  }
  
}
