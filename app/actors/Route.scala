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

class Routes extends Actor {

  val log = Logging(context.system, this)

  var connected = Map.empty[Flight, (Option[DateTime], Concurrent.Channel[FlightPoint])]
 
  def receive = {
    case Track(flight) => {
      val e = Concurrent.unicast[FlightPoint] {c =>
        log.info("Start")
        connected = connected + (flight -> (None, c))
      }
      sender ! Connected(e)
    }
 
    case Stop(flight) => {
      connected = connected - flight
    }
 
    case Route(flight, points) => {
      for((lastOpt, channel) <- connected.get(flight)) { 
        lastOpt match {
          case Some(last) =>
            points.filter(_.timestamp.map(_.isAfter(last)) getOrElse false).foreach(channel.push)
          case None =>
            points.foreach(channel.push)
        }
        connected = connected - flight + (flight -> (points.last.timestamp, channel))
      }
    }
  }
}

case class Route(flight: Flight, points: Seq[FlightPoint])
case class Connected(enumerator: Enumerator[FlightPoint])
case class Track(flight: Flight)
case class Stop(flight: Flight)
