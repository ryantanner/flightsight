package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.event.Logging 
 
import scala.concurrent.duration._
import scala.concurrent.Future
 
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import org.joda.time.DateTime

import play.api.Logger

import models._

class Points(source: ActorRef) extends Actor {

  val log = Logger

  var connected = Map.empty[Flight, (Set[POI], Iteratee[POI, Unit])]

  def receive = {
    case Track(flight) => {
      log.info("Start")

      val i = Iteratee.foreach[POI] { poi =>
        val jsPOI = Json.toJson(poi)

        (for {
          json <- jsPOI.transform(Event.addEventProperties(poi))
        } yield json) fold (
          error => throw new Exception(error.mkString("\n")),
          js => source ! Stream(flight, js)
        )
      }

      connected = connected + (flight -> (Set[POI](), i))
    }
 
    case Stop(flight) => {
      for ((set, i) <- connected.get(flight)) {
        Enumerator.eof[POI] |>> i
      }
      connected = connected - flight
    }
 
    case POIs(flight, points) => {
      log.debug(s"received ${points.size} for ${flight.ident}")
      for((set, i) <- connected.get(flight)) { 
        Enumerator((points &~ set).toList:_*) |>> i
        connected = connected - flight + (flight -> (set | points, i))
      }
    }

    case Update(flight, ne, sw) => {
      log.debug(s"Received updated bounds for ${flight.ident}")
      for ((set, i) <- connected.get(flight)) {
        POI.featuresWithin(ne, sw) map { case (concise, all) =>
          log.debug(s"Found ${all.length} points")
          if(concise.isEmpty)
            self ! POIs(flight, all.toSet)
          else
            self ! POIs(flight, concise.toSet)
        }
      }
    }
  }
}

case class POIs(flight: Flight, points: Set[POI])
case class Update(flight: Flight, ne: GeoPoint, sw: GeoPoint)

