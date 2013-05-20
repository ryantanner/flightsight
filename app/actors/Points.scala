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

  val log = Logging(context.system, this)

  var connected = Map.empty[Flight, (Set[POI], Enumerator[POI])]
 
  def receive = {
    case _ => { } 
    /*
    case Track(flight) => {
      log.info("Start")
      val e = Enumerator[POI]()
      connected = connected + (flight -> (Set[POI](), e))
      sender ! Ready[POI](e)
      source ! Stream(flight, e &> Json.toJson)
    }
 
    case Stop(flight) => {
      for ((set, e) <- connected.get(flight)) {
        e >>> Enumerator.eof[POI]
      }
      connected = connected - flight
    }
 
    case Push(flight, points) => {
      for((set, e) <- connected.get(flight)) { 
        e >>> Enumerator((set &~ points).toList:_*)
        connected = connected - flight + (flight -> (set & points, e))
      }
    }
    */
  }
}

case class Push(flight: Flight, points: Set[POI])
