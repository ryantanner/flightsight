package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json._
import play.api.libs.iteratee._

import play.api.Logger

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.Future

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._

import org.joda.time.{DateTime => JodaDateTime}

import models._
import models.transformers._

object Flights extends Controller {

  /* Forms */
  val flightForm = Form(
    tuple(
      "airline"      -> text,
      "flightNumber" -> number.verifying(min(0)),
      "date"         -> jodaDate("yyyy-MM-dd")
    ) 
  )

  /* Controller Actions */
  def handleFlightForm = Action { implicit request =>
    flightForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.flightSelector(formWithErrors)),
      value => Redirect(routes.Flights.find(value._1, value._2, value._3))
    )
  }

  def find(airlineCode: String, flightNumber: Int, departureDate: JodaDateTime) = Action {
    Async {
      Airlines.findByICAO(airlineCode) flatMap { maybeAirline =>
        maybeAirline map { airline =>
          for {
            flights         <- Flight.findByFlightNumber(airline, flightNumber, departureDate)
            withAirportInfo <- Future.sequence(flights.map(f => f.withAirportInfo))
            airlineInfo     <- airline.withInfo
          } yield withAirportInfo match {
            case List(f)        => Redirect(routes.Flights.map(airline.icao, f._2.icao, f._3.icao, f._1.number, departureDate))
            case head :: tail   => Ok(views.html.multipleFlights(head :: tail, airlineInfo, departureDate))
            case Nil            => Ok(views.html.flightNotFound(airline, flightNumber, departureDate))
            case _              => BadRequest("oops")
          }
        } getOrElse future(BadRequest("No such airline $airlineCode"))
      }
    }
  }

  // Searches for a unique Flight from a ScheduledFlight
  def search(flight: ScheduledFlight) = NotImplemented

  def findByRoute(airlineCode: String,  origCode: String, destCode: String, departureDate: JodaDateTime) = Action {
    Async {
      for {
        airline         <- Airlines.findByICAO(airlineCode)
        destination     <- Airports.findByICAO(destCode)
        origin          <- Airports.findByICAO(origCode)
        flights         <- FlightAware.findByRoute(airline.get, destination.get, origin.get, departureDate)
        originInfo      <- origin.get.withInfo
        destinationInfo <- destination.get.withInfo
        if airline.isDefined && destination.isDefined && origin.isDefined
      } yield flights match {
        case flight :: Nil => Redirect(routes.Flights.find(airline.get.icao, flight.number, flight.departureTime))
        case head :: tail => Ok(views.html.routeSchedule(head :: tail, airline.get, originInfo, destinationInfo, departureDate))
      }
    }
  }

  def map(airlineCode: String, originCode: String, destinationCode: String, flightNumber: Int, date: JodaDateTime) = Action {
    NotImplemented
  }

}
