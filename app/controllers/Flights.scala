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
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.Future

import org.joda.time.DateTime

import models._

object Flights extends Controller {

  val flightForm = Form(
    tuple(
      "airline"      -> text,
      "flightNumber" -> number.verifying(min(0)),
      "date"         -> jodaDate
    ) 
  )

  def handleFlightForm = Action { implicit request =>
    flightForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.flightSelector(formWithErrors)),
      value => Async {
        for {
          maybeAirline <- Airlines.findByICAO(value._1)
        } yield maybeAirline map { airline => 
          Redirect(routes.Flights.find(airline, value._2, value._3))
        } getOrElse(NotFound)
      }
    )
  }

  def find(airline: Airline, flightNumber: Int, departureDate: DateTime) = Action {
    val futureOfFlights = FlightAware.findByFlightNumber(airline, flightNumber, departureDate)
    val futureAirlineInfo = airline.withInfo
    Async {
      for {
        flights     <- futureOfFlights
        airlineInfo <- futureAirlineInfo
      } yield flights match {
          case flight :: Nil => Ok(views.html.map(flight, airlineInfo))
          case head :: tail  => Ok(views.html.multipleFlights(head :: tail, airlineInfo, departureDate))
          case Nil           => Ok(views.html.flightNotFound(airline, flightNumber, departureDate))
      }
    }
  }

  // Searches for a unique Flight from a ScheduledFlight
  def search(flight: ScheduledFlight) = NotImplemented

  def findByRoute(airline: Airline, destination: Airport, origin: Airport, departureDate: DateTime) = Action {
    val futureOfScheduledFlights = FlightAware.findByRoute(airline, destination, origin, departureDate)
    val futureOriginInfo = origin.withInfo
    val futureDestinationInfo = destination.withInfo

    Async {
      for {
        flights         <- futureOfScheduledFlights
        originInfo      <- futureOriginInfo
        destinationInfo <- futureDestinationInfo
      } yield flights match {
        case flight :: Nil => Redirect(routes.Flights.find(airline, flight.ident.filter(_.isDigit).toInt, flight.departureTime))
        case head :: tail => Ok(views.html.routeSchedule(head :: tail, airline, originInfo, destinationInfo, departureDate))
      }
    }
  }

  def map(flightNumber: String, departureDate: DateTime, faFlightID: String) = Action {
    NotImplemented
  }

}
