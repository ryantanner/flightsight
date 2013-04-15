package models

import play.api.mvc._
import play.api.Play.current
import play.api.libs.json.Reads._
// if you need Json structures in your scope
import play.api.libs.json._
// IMPORTANT import this to have the required tools in your scope
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError
import play.api.libs.ws._
import play.api.Logger

import reactivemongo.api._
import reactivemongo.bson._

import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import scala.concurrent._
import play.api.libs.concurrent.Execution.Implicits._

import org.joda.time.{DateTime => JodaDateTime}
import com.ning.http.client.Realm.AuthScheme

import Airport._
import AirportInfo._
import Airline._
//import Flight._

import DateTime._

import transformers._
//import formatters._

object FlightAware {

 val endpoint = "http://flightxml.flightaware.com/json/FlightXML2/"

  def findByFlightNumber(airline: Airline, flightNumber: Int, departureDate: JodaDateTime, airports: Option[(Airport, Airport)] = None): Future[List[Flight]] = {
    Logger.debug(s"Requesting flights by number for ${airline.icao} $flightNumber on ${departureDate.toLocalDate.toString}")
    val params = List(
      ("ident", airline.icao + flightNumber),
      ("howMany", "15"))

    def requestFlights(params: List[(String, String)], offset: Int = 0): Future[List[Flight]] = {
      Logger.debug("Requesting flights with offset " + offset)

      val request = wsAuth(endpoint + "FlightInfoEx")
        .withQueryString(params:_*)
        .withQueryString(("offset" -> offset.toString))
        .get

      val futureFlights = request map { response => 
        // Check that response doesn't contain an error
        (response.json \ "error").asOpt[String] match {
          case Some(error) => (Nil, -1)
          case None => {
            val data = response.json \ "FlightInfoExResult"

            val addFields = (__).json.update(
              addMongoId andThen
              addCreationDate
            )

            val flights = for {
              array <- (data \ "flights").transform(readJsArrayMap(addFields))
              flights <- array.validate[Seq[Flight]](validateJsArrayMap[Flight](Flight.faFlightReads))
            } yield flights 

            val nextOffset = (data \ "next_offset").as[Int]

            flights fold (
              error => throw new Exception(error.mkString("\n")),
              success => (success, nextOffset)
            ) 
          }
        }
      }

      futureFlights flatMap { case (flights, nextOffset) =>
        
        flights foreach (f => Logger.debug(f.toString))

        // toss them all into mongo
        Flight.insert(flights)

        val flightsBeforeDepartureDate = flights filter { f =>
          f.filedDepartureTime.toLocalDate
           .isBefore(departureDate.toLocalDate)
        }

        val flightsOnDepartureDate = airports match {
          case None => flights filter { f =>
            f.filedDepartureTime.toLocalDate
             .equals(departureDate.toLocalDate)
          }
          case Some((orig, dest)) => flights filter { f =>
            f.filedDepartureTime.toLocalDate.equals(departureDate.toLocalDate) &&
            f.origin == orig.icao &&
            f.destination == dest.icao
          }
        }

        val flightsAfterDepartureDate = flights filter { f =>
          f.filedDepartureTime.toLocalDate
          .isAfter(departureDate.toLocalDate)
        }

        Logger.debug("Flights found on date: " + flightsOnDepartureDate.length)
        Logger.debug("Flights found after date: " + flightsAfterDepartureDate.length)
        Logger.debug("Flights found before date: " + flightsBeforeDepartureDate.length)

        flightsOnDepartureDate match {
          case head :: tail => Future.successful(head :: tail) // found flights on given date, return those
          case Nil          => 
            if (flightsBeforeDepartureDate.isEmpty && flightsAfterDepartureDate.isEmpty) 
              Future.successful(Nil) // no applicable flights
            else if (flightsBeforeDepartureDate.nonEmpty && flightsAfterDepartureDate.isEmpty) // all flights before given date, move to next offset
              requestFlights(params, nextOffset)
            else 
              Future.successful(Nil) // no applicable flights
        }

      } recover {
        case t:Throwable => throw t
      }
          
    }

    requestFlights(params) 
  }

  def findByRoute(airline: Airline, destination: Airport, origin: Airport, departureDate: JodaDateTime): Future[List[ScheduledFlight]] = {
    Logger.debug(s"Requesting flights by route for ${airline.icao} btwn ${destination.icao} and ${origin.icao} on ${departureDate.toLocalDate.toString}")
    val params = List[(String,String)](
      ("startDate", departureDate.getMillis.toString), // get epoch of 00:01 of depatureDate
      ("endDate", departureDate.getMillis.toString), // get epoch of 23:59 of departureDate
      ("origin", origin.icao),
      ("destination", destination.icao),
      ("airline", airline.icao),
      ("howMany", "15"),
      ("offset", "0"))

    wsAuth(endpoint + "AirlineFlightSchedules")
      .withQueryString(params:_*)
      .get.map { response => 
        //Logger.debug(response.body)
        
        val flights = (for {
          flights <- ((response.json \ "data").validate[List[ScheduledFlight]].asOpt.toRight("AirlineFlightSchedules call expected JsArray[ScheduledFlight]")).right
        } yield flights)

        flights fold (
          error => throw new Exception(error),
          flights => flights
        )
      }
  }

  def airportInfo(airport: Airport): Future[AirportInfo] = {
    Logger.debug(s"Requesting airport info for ${airport.icao}")
    val params = List(("airportCode", airport.icao))

    wsAuth(endpoint + "AirportInfo")
    .withQueryString(params:_*)
    .get.map { response =>
      Logger.debug(response.body)

      val update = (__).json.update(
        addMongoId andThen
        icaoTransformer(airport.icao) andThen
        addCreationDate
      )

      (response.json \ "AirportInfoResult").transform(update) fold (
        error => throw new Exception(error.mkString("\n")),
        success => success.as[AirportInfo]
      )

    } recoverWith {
      case t:Throwable => Future.failed(t)
    }
  }

  def airlineInfo(airline: Airline): Future[AirlineInfo] = {
    Logger.debug(s"Requesting airline info for ${airline.icao}")
    val params = List(("airlineCode", airline.icao))

    wsAuth(endpoint + "AirlineInfo")
    .withQueryString(params:_*)
    .get.map { response =>
      //Logger.debug(response.toString)

      val update = (__).json.update(
        addMongoId andThen 
        icaoTransformer(airline.icao) andThen
        addCreationDate
      )

      Logger.debug("Transformed: " + (response.json \ "AirlineInfoResult").transform(update).get.toString)

      (response.json \ "AirlineInfoResult").transform(update) fold (
        error => throw new Exception(error.mkString("\n")),
        success => success.as[AirlineInfo]
      )
      
    } recoverWith {
      case t:Throwable => Future.failed(t)
    }
  }

  def wsAuth(url: String): WS.WSRequestHolder = {
    WS.url(url).withAuth("ryantanner", "96507582af80a489d35bac3761f91052a2ebc1d1", AuthScheme.BASIC)
  }

  val sampleFlights = """{"FlightInfoExResult":{"next_offset":15,"flights":[{"faFlightID":"UAL1102-1365830980-airline-0065","ident":"UAL1102","aircrafttype":"B738","filed_ete":"03:24:00","filed_time":1365830980,"filed_departuretime":1366071060,"filed_airspeed_kts":409,"filed_airspeed_mach":"","filed_altitude":0,"route":"","actualdeparturetime":0,"estimatedarrivaltime":1366083900,"actualarrivaltime":0,"diverted":"","origin":"KIAH","destination":"KBOS","originName":"Houston Bush Int'ctl","originCity":"Houston, TX","destinationName":"Boston Logan Intl","destinationCity":"Boston, MA"},{"faFlightID":"UAL1102-1365830980-airline-0064","ident":"UAL1102","aircrafttype":"B735","filed_ete":"00:40:00","filed_time":1365830980,"filed_departuretime":1366062600,"filed_airspeed_kts":248,"filed_airspeed_mach":"","filed_altitude":0,"route":"","actualdeparturetime":0,"estimatedarrivaltime":1366065600,"actualarrivaltime":0,"diverted":"","origin":"KSAT","destination":"KIAH","originName":"San Antonio Intl","originCity":"San Antonio, TX","destinationName":"Houston Bush Int'ctl","destinationCity":"Houston, TX"},{"faFlightID":"UAL1102-1365744739-airline-0202","ident":"UAL1102","aircrafttype":"B752","filed_ete":"01:46:00","filed_time":1365886863,"filed_departuretime":1365973200,"filed_airspeed_kts":262,"filed_airspeed_mach":"","filed_altitude":0,"route":"COATE J36 FNT WYNDE4","actualdeparturetime":0,"estimatedarrivaltime":1365981240,"actualarrivaltime":0,"diverted":"","origin":"KEWR","destination":"KORD","originName":"Newark Liberty Intl","originCity":"Newark, NJ","destinationName":"Chicago O'Hare Intl","destinationCity":"Chicago, IL"},{"faFlightID":"UAL1102-1365658352-airline-0204","ident":"UAL1102","aircrafttype":"B752","filed_ete":"02:15:00","filed_time":1365872954,"filed_departuretime":1365872340,"filed_airspeed_kts":462,"filed_airspeed_mach":"","filed_altitude":380,"route":"COATE J36 FNT WYNDE4","actualdeparturetime":1365873540,"estimatedarrivaltime":1365880415,"actualarrivaltime":1365880320,"diverted":"","origin":"KEWR","destination":"KORD","originName":"Newark Liberty Intl","originCity":"Newark, NJ","destinationName":"Chicago O'Hare Intl","destinationCity":"Chicago, IL"},{"faFlightID":"UAL1102-1365658352-airline-0203","ident":"UAL1102","aircrafttype":"B738","filed_ete":"02:35:00","filed_time":1365850982,"filed_departuretime":1365851460,"filed_airspeed_kts":445,"filed_airspeed_mach":"","filed_altitude":370,"route":"CSHEL4 ORL J53 BARBS J53 CRG J51 SAV J207 FLO J55 TUBAS J51 FAK PHLBO3","actualdeparturetime":1365851520,"estimatedarrivaltime":1365858660,"actualarrivaltime":1365858911,"diverted":"","origin":"KRSW","destination":"KEWR","originName":"Southwest Florida Intl","originCity":"Fort Myers, FL","destinationName":"Newark Liberty Intl","destinationCity":"Newark, NJ"},{"faFlightID":"UAL1102-1365571853-airline-0218","ident":"UAL1102","aircrafttype":"B738","filed_ete":"03:21:00","filed_time":1365817957,"filed_departuretime":1365812040,"filed_airspeed_kts":445,"filed_airspeed_mach":"","filed_altitude":370,"route":"LFK6 LIT J131 PXV J29 JHW J82 ALB QUABN2","actualdeparturetime":1365818520,"estimatedarrivaltime":1365829129,"actualarrivaltime":1365829131,"diverted":"","origin":"KIAH","destination":"KBOS","originName":"Houston Bush Int'ctl","originCity":"Houston, TX","destinationName":"Boston Logan Intl","destinationCity":"Boston, MA"},{"faFlightID":"UAL1102-1365571853-airline-0217","ident":"UAL1102","aircrafttype":"B735","filed_ete":"01:04:00","filed_time":1365802170,"filed_departuretime":1365801600,"filed_airspeed_kts":441,"filed_airspeed_mach":"","filed_altitude":300,"route":"TBDDP JEPEG WOLDE3","actualdeparturetime":1365802620,"estimatedarrivaltime":1365805835,"actualarrivaltime":1365805980,"diverted":"","origin":"KMSY","destination":"KIAH","originName":"New Orleans Intl","originCity":"New Orleans, LA","destinationName":"Houston Bush Int'ctl","destinationCity":"Houston, TX"},{"faFlightID":"UAL1102-1365485614-airline-0215","ident":"UAL1102","aircrafttype":"B738","filed_ete":"03:11:00","filed_time":1365726284,"filed_departuretime":1365725460,"filed_airspeed_kts":446,"filed_airspeed_mach":"","filed_altitude":370,"route":"LOA7 BYP MLC RZC STL VHP ROD J29 JHW J82 ALB QUABN2","actualdeparturetime":1365726840,"estimatedarrivaltime":1365738761,"actualarrivaltime":1365738600,"diverted":"","origin":"KIAH","destination":"KBOS","originName":"Houston Bush Int'ctl","originCity":"Houston, TX","destinationName":"Boston Logan Intl","destinationCity":"Boston, MA"},{"faFlightID":"UAL1102-1365485614-airline-0214","ident":"UAL1102","aircrafttype":"B738","filed_ete":"00:43:00","filed_time":1365717306,"filed_departuretime":1365717000,"filed_airspeed_kts":370,"filed_airspeed_mach":"","filed_altitude":230,"route":"HUBEE2 HAMMU HAMMU1","actualdeparturetime":1365717780,"estimatedarrivaltime":1365720123,"actualarrivaltime":1365720120,"diverted":"","origin":"KSAT","destination":"KIAH","originName":"San Antonio Intl","originCity":"San Antonio, TX","destinationName":"Houston Bush Int'ctl","destinationCity":"Houston, TX"},{"faFlightID":"UAL1102-1365390315-airline-0024","ident":"UAL1102","aircrafttype":"B739","filed_ete":"03:09:00","filed_time":1365604290,"filed_departuretime":1365602220,"filed_airspeed_kts":438,"filed_airspeed_mach":"","filed_altitude":370,"route":"SEA J90 MWH LKT DBS BPI GWEDO FRNCH1","actualdeparturetime":1365605160,"estimatedarrivaltime":1365612720,"actualarrivaltime":1365612999,"diverted":"","origin":"KSEA","destination":"KDEN","originName":"Seattle-Tacoma Intl","originCity":"Seattle, WA","destinationName":"Denver Intl","destinationCity":"Denver, CO"},{"faFlightID":"UAL1102-1365312535-airline-0216","ident":"UAL1102","aircrafttype":"B752","filed_ete":"01:50:00","filed_time":1365563346,"filed_departuretime":1365562980,"filed_airspeed_kts":459,"filed_airspeed_mach":"","filed_altitude":360,"route":"SHOR4 RBL LMT HAWKZ2","actualdeparturetime":1365564180,"estimatedarrivaltime":1365570072,"actualarrivaltime":1365570240,"diverted":"","origin":"KSFO","destination":"KSEA","originName":"San Francisco Intl","originCity":"San Francisco, CA","destinationName":"Seattle-Tacoma Intl","destinationCity":"Seattle, WA"},{"faFlightID":"UAL1102-1365312535-airline-0215","ident":"UAL1102","aircrafttype":"B739","filed_ete":"04:07:00","filed_time":1365545878,"filed_departuretime":1365543780,"filed_airspeed_kts":459,"filed_airspeed_mach":"","filed_altitude":340,"route":"IOW J10 OBH J100 SNY CHE J24 MTU J148 DTA RUMPS OAL MOD4","actualdeparturetime":1365545880,"estimatedarrivaltime":1365560160,"actualarrivaltime":1365560340,"diverted":"","origin":"KORD","destination":"KSFO","originName":"Chicago O'Hare Intl","originCity":"Chicago, IL","destinationName":"San Francisco Intl","destinationCity":"San Francisco, CA"},{"faFlightID":"UAL1102-1365312535-airline-0214","ident":"UAL1102","aircrafttype":"B738","filed_ete":"01:28:00","filed_time":1365534637,"filed_departuretime":1365532980,"filed_airspeed_kts":447,"filed_airspeed_mach":"","filed_altitude":360,"route":"AML J149 ROD WATSN1","actualdeparturetime":1365534960,"estimatedarrivaltime":1365540720,"actualarrivaltime":1365540720,"diverted":"","origin":"KDCA","destination":"KORD","originName":"Reagan National","originCity":"Washington, DC","destinationName":"Chicago O'Hare Intl","destinationCity":"Chicago, IL"},{"faFlightID":"UAL1102-1365226051-airline-0209","ident":"UAL1102","aircrafttype":"B738","filed_ete":"03:27:00","filed_time":1365466680,"filed_departuretime":1365466800,"filed_airspeed_kts":441,"filed_airspeed_mach":"","filed_altitude":370,"route":"LFK6 LIT J131 PXV J29 JHW J82 ALB QUABN2","actualdeparturetime":1365467100,"estimatedarrivaltime":1365478307,"actualarrivaltime":1365478260,"diverted":"","origin":"KIAH","destination":"KBOS","originName":"Houston Bush Int'ctl","originCity":"Houston, TX","destinationName":"Boston Logan Intl","destinationCity":"Boston, MA"},{"faFlightID":"UAL1102-1365139664-airline-0148","ident":"UAL1102","aircrafttype":"B738","filed_ete":"03:22:00","filed_time":1365380587,"filed_departuretime":1365380460,"filed_airspeed_kts":440,"filed_airspeed_mach":"","filed_altitude":370,"route":"ELD3 ELD J29 MEM J42 RBV J222 JFK KRANN3","actualdeparturetime":1365381180,"estimatedarrivaltime":1365392533,"actualarrivaltime":1365392760,"diverted":"","origin":"KIAH","destination":"KBOS","originName":"Houston Bush Int'ctl","originCity":"Houston, TX","destinationName":"Boston Logan Intl","destinationCity":"Boston, MA"}]}}"""

}
