package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
//import play.api.libs.json.Writes._
//import play.api.libs.json.Format._
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError

import play.api.Logger

import reactivemongo.api._
import reactivemongo.bson._

import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._

import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

import org.joda.time.{DateTime => JodaDateTime}
import DateTimeZone._

object transformers {

  def icaoTransformer(icao: String): Reads[JsObject] = (__).json.update(
    __.read[JsObject].map { o => o ++ Json.obj("icao" -> icao) }
  )

  def addMongoId: Reads[JsObject] = (__).json.update(
    __.read[JsObject].map { o => o ++ Json.obj(
      "id" -> Json.obj("$oid" ->
      BSONObjectID.generate.stringify)) }
  )

  def addFaFlightId(faFlightId: String): Reads[JsObject] = (__).json.update(
    __.read[JsObject].map { o => o ++ Json.obj("faFlightId" -> faFlightId)}
  )

  def addNotPlanned: Reads[JsObject] = (__).json.update(
    __.read[JsObject].map { o => o ++ Json.obj("planned" -> false)}
  )

  def addIsPlanned: Reads[JsObject] = (__).json.update(
    __.read[JsObject].map { o => o ++ Json.obj("planned" -> true)}
  )

  def addCreationDate: Reads[JsObject] = (__).json.update(
    __.read[JsObject].map { o => o ++ Json.obj("creationDate" ->
      new JodaDateTime
    )}
  )

  def secondsToMilliseconds: Reads[JsObject] = (__).json.update(
    of[JsNumber].map { case JsNumber(secs) => {
      Logger.debug("converting seconds to milliseconds")
      JsNumber(secs * 1000) 
    }}
  )

  val latLngToGeoPoint: Reads[JsObject] = 
    (__ \ 'location \ 'coordinates).json.copyFrom(
       ((__ \ "latitude").json.pick and       
       (__ \ "longitude").json.pick).tupled.map{ case(lat, long) => Json.arr(long, lat) }
    ).andThen(__.json.update((__ \ 'location \ 'type).json.put(JsString("Point"))))

  // From here:
  // https://gist.github.com/mandubian/5183939
  def readJsArrayMap[A <: JsValue](transformEach: Reads[A]): Reads[JsArray] = Reads { js => js match {
    case arr: JsArray =>
      arr.value.foldLeft(JsSuccess(Seq[JsValue]()): JsResult[Seq[JsValue]]) { (acc, e) => 
        acc.flatMap{ seq => 
          e.transform(transformEach).map( v => seq :+ v )
        }
      }.map(JsArray(_))
    case _ => JsError("expected JsArray")
  }}

  def validateJsArrayMap[A](validateEach: Reads[A]): Reads[Seq[A]] = Reads { js => js match {
    case arr: JsArray =>
      arr.value.foldLeft(JsSuccess(Seq[A]()): JsResult[Seq[A]]) { (acc, e) =>
        acc.flatMap { seq =>
          e.validate(validateEach).map( v => seq :+ v)
        }
      }.map(x => x)
    case _ => JsError("expected JsArray")
  }}


//}

/* Impliciit JSON formatters */
//object formatters {



  /* JSON formatters and transformers */
  //implicit val flightFormat = Json.format[Flight]
  //implicit val scheduledFlightFormat = Json.format[ScheduledFlight]



}
