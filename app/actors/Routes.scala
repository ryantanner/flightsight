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

import play.api.Logger

import models._

class Routes(source: ActorRef) extends Actor {

  val log = Logger
  //val log = Logging(context.system, this)

  var connected = Map.empty[Flight, (Option[DateTime], Iteratee[FlightPoint, Unit])]

  var retrievers = Map.empty[Flight, Cancellable]

  def receive = LoggingReceive {
    case Track(flight) => {
      if(!connected.isDefinedAt(flight)) {
        log.info(s"Now tracking ${flight.ident}")

        Flight.route(flight)(self)

        val i = Iteratee.foreach[FlightPoint] { point =>
          //log.debug(s"pushing ${point.id}")
          val jsPoint = Json.toJson(point)

          (for {
            json <- jsPoint.transform(Event.addEventProperties(point))
          } yield json) fold (
            error => throw new Exception(error.mkString("\n")),
            js => source ! Stream(flight, js)
          )
        }
        connected = connected + (flight -> (None, i))      
        //retrievers = retrievers + (flight -> retriever)

        /*
        context.system.scheduler.scheduleOnce(
          10 minutes,
          self,
          Stop(flight)
        )
        */
      }
      //source ! Channel(flight)
    }

    /*
    case Ready(flight, channel) => {
      val i = Iteratee.foreach[FlightPoint] { point =>
        log.debug(s"pushing ${point.id}")
        source ! Stream(flight, Json.toJson(point))
        //channel.push(Json.toJson(point))
      }
      connected = connected + (flight -> (None, i))
    }
    */
 
    case Tracking(flight, retriever) => {
      retrievers = retrievers + (flight -> retriever)
    }

    case Stop(flight) => {
      log.info(s"stopping flight tracking for ${flight.ident}")
      for ((lastOpt, points) <- connected.get(flight)) {
        Enumerator.eof[FlightPoint] |>> points
      }
      connected = connected - flight
      
      for (retriever <- retrievers.get(flight)) {
        log.info("cancelling FlightAware retrieval")
        retriever.cancel
      }
    }

    case Route(flight, points) => {
      for((lastOpt, iteratee) <- connected.get(flight)) { 
        log.debug(s"actor found ${points.size} points")

        val filter = Enumeratee.filter[FlightPoint]( (point) =>
          (for {
            last <- lastOpt
            time <- point.timestamp
          } yield time.isAfter(last)) getOrElse true
        )

        Enumerator(points:_*) &> filter apply iteratee

        /*
        Enumerator(points:_*) &> Enumeratee.drop[FlightPoint](points.size - 2) &>
                                 Enumeratee.map[FlightPoint] { point => point.location } |>>
                                 Iteratee.foreach[GeoPoint] { geo =>
                                   log.debug("asking for updated points of interest")

                                   pointStream ! Update(flight, geo)
                                 }
                                 */

        connected = connected - flight + (flight -> (points.last.timestamp, iteratee))
        /*
        lastOpt match {
          case Some(last) =>
            channel >>> Enumerator(points.filter(_.timestamp.map(_.isAfter(last)) getOrElse false):_*)
          case None =>
            channel >>> Enumerator(points:_*)
        } foreach { channel =>
          connected = connected - flight + (flight -> (points.last.timestamp, channel))
        }
        */
      }
    }

  }
}

case class Route(flight: Flight, points: Seq[FlightPoint])
case class Tracking(flight: Flight, retriever: Cancellable)
