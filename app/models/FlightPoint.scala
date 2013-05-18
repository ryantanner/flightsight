package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
//import play.api.libs.json.Writes._
//import play.api.libs.json.Format._
import play.api.libs.functional.syntax._
import play.api.libs.iteratee._

import play.api.data.validation.ValidationError
import play.api.mvc.PathBindable
import play.api.Play.current

import play.api.Logger

import org.joda.time.{DateTimeZone => JodaTimeZone, DateTime => JodaDateTime}
import org.joda.time.Period

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import reactivemongo.api._
import reactivemongo.bson._

import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import DateTimeZone._
import DateTime._

import transformers._
import POI.geoPointFormat

case class FlightPoint(
  id: BSONObjectID,
  faFlightId: String,
  altitude: Option[Int],
  altitudeChange: Option[String],
  altitudeStatus: Option[String],
  groundspeed: Option[Int],
  location: GeoPoint,
  timestamp: Option[JodaDateTime],
  updateType: Option[String],
  planned: Boolean,
  creationDate: JodaDateTime)

object FlightPoint {

  implicit val flightPointReads = Json.reads[FlightPoint] 
  implicit val flightPointWrites = Json.writes[FlightPoint] 

  /* Mongo Collections */
  val db = ReactiveMongoPlugin.db
  val points: JSONCollection = db.collection[JSONCollection]("flightPoints")

  val faFlightPointReads: Reads[FlightPoint] = (
    (__ \ "id").read[BSONObjectID] ~
    (__ \ "faFlightId").read[String] ~
    (__ \ "altitude").readNullable[Int] ~
    (__ \ "altitudeChange").readNullable[String] ~
    (__ \ "altitudeStatus").readNullable[String] ~
    (__ \ "groundspeed").readNullable[Int] ~
    (__ \ "location").read[GeoPoint] ~
    (__ \ "timestamp").readNullable[JodaDateTime](DateTime.secondsJodaDateReads) ~
    (__ \ "updateType").readNullable[String] ~
    (__ \ "planned").read[Boolean] ~
    (__ \ "creationDate").read[JodaDateTime]
  )(FlightPoint.apply _)

  def insert(point: FlightPoint) = {
    Logger.debug(s"Inserting point for ${point.faFlightId}")
    points.insert(point)
  }

}
