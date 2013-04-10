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
      "airline"      -> text,
      "flightNumber" -> number.verifying(min(0)),
      "date"         -> jodaDate
    ) verifying("Invalid airline", fields => fields match {
      case (a,f,d) => Airline.findByICAO(a).isDefined
    })
  )

  def handleFlightForm = Action { implicit request =>
    flightForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.flightSelector(formWithErrors)),
      value => Redirect(routes.Flights.find(Airline.findByICAO(value._1).get, value._2, value._3))
    )
  }

  def find(airline: Airline, flightNumber: Int, departureDate: DateTime) = Action {
    val futureOfFlights = FlightAware.findByFlightNumber(airline, flightNumber, departureDate)
    Async {
      futureOfFlights map { flights =>
        flights match {
          case flight :: Nil => Ok(views.html.map(flight))
          case head :: tail  => Ok(views.html.multipleFlights(head :: tail, airline, departureDate))
          case Nil           => Ok(views.html.flightNotFound(airline, flightNumber, departureDate))
        }
      }
    }
  }

  def listAirlines = Action {
    val airlines = Airline.all
    val json = Json.obj(
      "airlines" -> Json.arr(
        airlines.map(a => Json.obj("name" -> a.name, "icao" -> a.code))
      )
    )
    Ok(json)
  }

  // Searches for a unique Flight from a ScheduledFlight
  def search(flight: ScheduledFlight) = NotImplemented

  def findByRoute(airline: Airline, destination: Airport, origin: Airport, departureDate: DateTime) = Action {
    val futureOfScheduledFlights = FlightAware.findByRoute(airline, destination, origin, departureDate)
    Async {
      futureOfScheduledFlights map { flights =>
        flights match {
          case flight :: Nil => Redirect(routes.Flights.find(airline, flight.ident.filter(_.isDigit).toInt, flight.departureTime))
          case head :: tail => Ok(views.html.routeSchedule(head :: tail, airline, origin, destination, departureDate))
        }
      }
    }
  }

  def map(flightNumber: String, departureDate: DateTime, faFlightID: String) = Action {
    NotImplemented
  }

}
