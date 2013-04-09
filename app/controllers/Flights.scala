package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json._
import play.api.libs.iteratee._

import scala.concurrent.ExecutionContext.Implicits.global

import org.joda.time.DateTime

import models._

object Flights {

  val flightForm = Form(
    tuple(
      "flightNumber" -> text.verifying(nonEmpty),
      "date"         -> jodaDate
    )
  )

  def handleFlightForm = Action { implicit request =>
    flightForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.flightSelector(formWithErrors)),
      value => Redirect(routes.Flights.find(value._1, value._2))
    )
  }

  def find(flightNumber: String, departureDate: DateTime) = Action {
    val futureOfFlights = FlightAware.findByFlightNumber(flightNumber, departureDate)
    Async {
      futureOfFlights map { flights =>
        flights match {
          case flight :: Nil => Ok(views.html.map(flight))
          case head :: tail  => Ok(views.html.multipleFlights(head :: tail))
          case Nil           => Ok(views.html.flightNotFound(flightNumber, departureDate))
        }
      }
    }
  }

  // Searches for a unique Flight from a ScheduledFlight
  def search(flight: ScheduledFlight) = NotImplemented

  def findByRoute(airline: Airline, destination: Airport, origin: Airport, departureDate: DateTime) = Action {
    val futureOfScheduledFlights = FlightAware.findByRoute(airline, destination, origin, departureDate)
    Async {
      futureOfScheduledFlights map { flights =>
        flights match {
          case flight :: Nil => Redirect(routes.Flights.find(flight.ident, flight.departureTime))
          case head :: tail => Ok(views.html.routeSchedule(head :: tail, airline, origin, destination, departureDate))
        }
      }
    }
  }

  def map(flightNumber: String, departureDate: DateTime, faFlightID: String) = Action {
    NotImplemented
  }

}
