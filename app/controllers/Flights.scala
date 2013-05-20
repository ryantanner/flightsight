package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.Comet.CometMessage
import play.api.libs.EventSource

import play.api.Logger
import play.api.Play.current

import scala.concurrent._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.Future
import akka.util.Timeout
import akka.actor.Props
import akka.pattern.ask

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._

import org.joda.time.{DateTime => JodaDateTime}

import models._
import models.transformers._
import actors._

object Flights extends Controller {

  /* Forms */
  val flightForm = Form(
    tuple(
      "airline"      -> text,
      "flightNumber" -> number.verifying(min(0)),
      "date"         -> jodaDate("yyyy-MM-dd")
    ) 
  )

  /* Actors */
  val eventSource = Akka.system.actorOf(Props[EventSource])
  val routeStream = Akka.system.actorOf(Props(new Routes(eventSource)))
  val pointStream = Akka.system.actorOf(Props(new Points(eventSource)))
  implicit val timeout = Timeout(1 second)
  
  /* Controller Actions */
  def handleFlightForm = Action { implicit request =>
    flightForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.flightSelector(formWithErrors)),
      value => Redirect(routes.Flights.findByFlightNumber(value._1, value._2, value._3))
    )
  }

  def findByFlightNumber(airlineCode: String, flightNumber: Int, departureDate: JodaDateTime) = Action {
    Logger.info(s"Finding flight by number for ${airlineCode} ${flightNumber} on ${departureDate}")
    Async {
      Airline.findByICAO(airlineCode) flatMap { maybeAirline =>
        maybeAirline map { airline =>
          for {
            flights         <- Flight.findByFlightNumber(airline, flightNumber, departureDate)
            withAirportInfo <- Future.sequence(flights.map(f => f.withAirportInfo))
            airlineInfo     <- airline.withInfo
          } yield (flights, withAirportInfo) match {
            case (List(f), List(ai))        => Redirect(routes.Flights.map(airline.icao, ai.origin.icao, ai.destination.icao, f.number, departureDate))
            case (head :: tail, airports)   => Ok(views.html.multipleFlights((head :: tail).zip(airports), airlineInfo, departureDate))
            case (Nil, _)                   => Ok(views.html.flightNotFound(airline, flightNumber, departureDate))
            case _                          => BadRequest("oops")
          }
        } getOrElse future(BadRequest(s"No such airline $airlineCode"))
      }
    }
  }

  // Searches for a unique Flight from a ScheduledFlight
  def search(flight: ScheduledFlight) = NotImplemented

  def findByRoute(airlineCode: String,  origCode: String, destCode: String, departureDate: JodaDateTime) = Action {
    Async {
      for {
        airline         <- Airline.findByICAO(airlineCode)
        destination     <- Airport.findByICAO(destCode)
        origin          <- Airport.findByICAO(origCode)
        flights         <- FlightAware.findByRoute(airline.get, destination.get, origin.get, departureDate)
        originInfo      <- origin.get.withInfo
        destinationInfo <- destination.get.withInfo
        if airline.isDefined && destination.isDefined && origin.isDefined
      } yield flights match {
        case flight :: Nil => Redirect(routes.Flights.map(airline.get.icao, originInfo.icao, destinationInfo.icao, flight.number, flight.departureTime))
        case head :: tail => Ok(views.html.routeSchedule(head :: tail, airline.get, originInfo, destinationInfo, departureDate))
      }
    }
  }

  def map(airlineCode: String, originCode: String, destinationCode: String, flightNumber: Int, date: JodaDateTime) = Action {
    Async {
      for {
        airline         <- Airline.findByICAO(airlineCode)
        origin          <- Airport.findByICAO(originCode)
        destination     <- Airport.findByICAO(destinationCode)
        airlineInfo     <- airline.get.withInfo
        originInfo      <- origin.get.withInfo
        destinationInfo <- destination.get.withInfo
        flight          <- Flight.findByNumberOriginDestination(airline.get, flightNumber, origin.get, destination.get, date)
        if airline.isDefined && origin.isDefined && destination.isDefined
      } yield flight map { f =>
        Ok(views.html.map(f, originInfo, destinationInfo, airlineInfo))
      } getOrElse (NotFound(s"Could not find $airlineCode $flightNumber on $date"))
    }
  }

  /*
  def flightRoute(airlineCode: String, originCode: String, destinationCode: String, flightNumber: Int, date: JodaDateTime) = Action {
    Async {
      for {
        airline         <- Airline.findByICAO(airlineCode)
        origin          <- Airport.findByICAO(originCode)
        destination     <- Airport.findByICAO(destinationCode)
        airlineInfo     <- airline.get.withInfo
        originInfo      <- origin.get.withInfo
        destinationInfo <- destination.get.withInfo
        flight          <- Flight.findByNumberOriginDestination(airline.get, flightNumber, origin.get, destination.get, date)
        if airline.isDefined && origin.isDefined && destination.isDefined
        maybeRoute      <- Flight.route(flight.get.faFlightId)
      } yield maybeRoute map { route =>
        Ok.stream((route &> toJson &> EventSource[JsValue]()(
          encoder = CometMessage.jsonMessages,
          eventNameExtractor = pointNameExtractor,
          eventIdExtractor = pointIdExtractor
        )) >>> Enumerator.eof).as("text/event-stream")
      } getOrElse NotFound
    }
  }
  */

  def source(airlineCode: String, originCode: String, destinationCode: String, flightNumber: Int, date: JodaDateTime) = Action {
    Async {
      for {
        airline         <- Airline.findByICAO(airlineCode)
        origin          <- Airport.findByICAO(originCode)
        destination     <- Airport.findByICAO(destinationCode)
        airlineInfo     <- airline.get.withInfo
        originInfo      <- origin.get.withInfo
        destinationInfo <- destination.get.withInfo
        flight          <- Flight.findByNumberOriginDestination(airline.get, flightNumber, origin.get, destination.get, date)
        if airline.isDefined && origin.isDefined && destination.isDefined
        source          <- (eventSource ? Track(flight.get))
        if airline.isDefined
      } yield source match { case Connected(stream) =>

        Flight.route(flight.get)(routeStream)
        //points ! Track(flight)

        Ok.stream((stream &> EventSource[JsValue]()(
          encoder = CometMessage.jsonMessages,
          eventNameExtractor = pointNameExtractor,
          eventIdExtractor = pointIdExtractor
        ))).as("text/event-stream")
        //Ok.stream(stream &> toEventSource &> EventSource[String]()).as("text/event-stream")
      } 
    }
  }

  val pointNameExtractor = EventSource.EventNameExtractor[JsValue]( (__) => Some((__ \ "eventType").as[String]))
  
  val pointIdExtractor = EventSource.EventIdExtractor[JsValue]( (event) => Some(event \ "id" \ "$oid" toString))


}
