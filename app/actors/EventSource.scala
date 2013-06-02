package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.event.Logging 
import akka.event.LoggingReceive
 
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

import scala.reflect.runtime.universe._

import play.api.Logger

import models._

class EventSource extends Actor {

  //val log = Logging(context.system, this)
  val log = Logger

  var connected = Map.empty[Flight, (Enumerator[JsValue], Concurrent.Channel[JsValue])]

  var sources = List.empty[ActorRef]

  def receive = LoggingReceive {
    case Track(flight) => {
      connected.get(flight) match {
        case Some((e, c)) => sender ! Connected(e)
        case None => {
          val (e, c) = Concurrent.broadcast[JsValue] 

          val (broadcastingEnumerator, broadcaster) =
            Concurrent.broadcast(e, (b) => {
              log.info(s"interest to zero on ${flight.ident}")
              self ! Stop(flight)
              b.close()
            })

          connected = connected + (flight -> (broadcastingEnumerator, c))
          sender ! Connected(broadcastingEnumerator)
        }
      }
    }

    case Channel(flight) => {
      for ((_, channel) <- connected.get(flight)) {
        sender ! Ready(flight, channel)
      }
    }
 
    case Stop(flight) => {
      log.info(s"stopping sources for ${flight.ident}")
      for ((enumerator, channel) <- connected.get(flight)) {
        channel.eofAndEnd
      }
      connected = connected - flight

      sources foreach { s => s ! Stop(flight) }
    }

    case Stream(flight, value) => {
      //log.debug("starting stream")
      for ((_, channel) <- connected.get(flight)) {
        //log.debug(s"streaming ${value.toString}")
        channel.push(value)
      }
    }

    case Register(actor) => {
      log.info(s"Registering ${actor.path.toString}")
      sources = sources :+ actor
    }
  }

  //private def toJson(e: E): Writes = e => Event.writer(e)

  /*
  private def stream(channel: Concurrent.Channel[JsValue]) = 
    Iteratee.foreach[JsValue](e => channel.push(e))
    */

}

case class Connected(enumerator: Enumerator[JsValue])
case class Ready(flight: Flight, enumerator: Concurrent.Channel[JsValue])
case class Track(flight: Flight)
case class Stop(flight: Flight)
case class Stream(flight: Flight, value: JsValue)
case class Channel(flight: Flight)
case class Register(actor: ActorRef)
