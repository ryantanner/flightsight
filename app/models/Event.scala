package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
//import play.api.libs.json.Format._
import play.api.libs.functional.syntax._

import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._


trait Event {
  val eventId: BSONObjectID
  val eventType: String
}

object Event {

  implicit val reader = (
    (__ \ "eventType").read[String] and 
    __.json.pick
  ).tupled flatMap { case (eventType, js) =>
   eventType match {
     case "routePoint" => Reads{ _ => Json.fromJson[FlightPoint](js) } map { c => c: Event } 
     case "interestPoint" => Reads{ _ => Json.fromJson[POI](js) } map { c => c: Event } 
   }
 }

  def writer(event: Event) =
    event.eventType match {
      case "routePoint" => Json.toJson[FlightPoint](FlightPoint.flightPointWrites)
      case "interestPoint" => Json.toJson[POI](POI.poiFormat)
    }

  def addEventProperties(event: Event): Reads[JsObject] = (__).json.update(
    __.read[JsObject].map { o => o ++ Json.obj("eventId" -> event.eventId) } andThen
    __.read[JsObject].map { o => o ++ Json.obj("eventType" -> event.eventType) }
  )

}
