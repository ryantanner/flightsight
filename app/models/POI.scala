package models

import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
//import play.api.libs.json.Writes._
//import play.api.libs.json.Format._
import play.api.libs.functional.syntax._

import play.api.data.validation.ValidationError
import play.api.mvc.PathBindable
import play.api.Play.current

import play.api.Logger

import org.joda.time.{DateTimeZone => JodaTimeZone, DateTime => JodaDateTime}
import org.joda.time.Period
import org.joda.time.format.DateTimeFormat

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import scala.language.postfixOps

import reactivemongo.api._
import reactivemongo.bson._

import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import java.io.File

import DateTimeZone._
import DateTime._

import transformers._

case class POI(
  id: Option[BSONObjectID],
  featureId: Int,
  name: String,
  featureClass: String,
  state: String,
  stateId: Int,
  county: String,
  countyId: Int,
  loc: GeoPoint,
  latitudeDMS: String,
  longitudeDMS: String,
  sourceLatitude: Option[Double],
  sourceLongitude: Option[Double],
  sourceLatitudeDMS: Option[String],
  sourceLongitudeDMS: Option[String],
  elevation: Int,
  elevationFT: Int,
  mapName: String,
  dateCreated: Option[JodaDateTime],
  dateEdited: Option[JodaDateTime])

case class GeoPoint(
  coordinates: List[Double], // always [long, lat]
  `type`: String = "Point" // stupid geojson forcing me to use "type"
) {

  def this(long: Double, lat: Double) = this(List(long, lat))

}

object GeoPoint {

  def latlng(long: Double, lat: Double) = this(List(long, lat))

  def unlatlng(point: GeoPoint) = Some((point.coordinates.head, point.coordinates.last))

}

object POI {

  val db = ReactiveMongoPlugin.db
  val pois: JSONCollection = db.collection[JSONCollection]("pois")
  val concisePois: JSONCollection = db.collection[JSONCollection]("concisePois")

  implicit val geoPointFormat = Json.format[GeoPoint]
  implicit val poiFormat = Json.format[POI]

  val features = Array("Arch", "Area", "Arroyo", "Bar", "Basin", "Bay", "Beach",
    "Bend", "Bridge", "Canal", "Cape", "Channel", "Civil", "Cliff", "Crater",
    "Dam", "Falls", "Flat", "Forest", "Gap", "Glacier", "Harbor", "Island",
    "Isthmus", "Lake", "Lava", "Locale", "Park", "Pillar", "Populated Place", "Plain", "Range", "Rapids",
    "Reserve", "Reservoir", "Ridge", "Sea", "Stream", "Summit", "Swamp",
    "Valley", "Woods")

  def featuresNear(point: GeoPoint): Future[(List[POI], List[POI])] = {
    val concise = concisePois.find(Json.obj(
      "loc" -> Json.obj(
        "$near" -> Json.obj(
          "$geometry" -> point, 
          "$maxDistance" -> 10000)
      )
    )).cursor[POI].toList

    val all = pois.find(Json.obj(
      "featureClass" -> Json.obj(
        "$in" -> features
      ),
      "loc" -> Json.obj(
        "$near" -> Json.obj(
          "$geometry" -> point, 
          "$maxDistance" -> 10000)
      )
    )).cursor[POI].toList

    concise zip all
  }

  def importPOI(file: File, collection: JSONCollection, missingElevation: Boolean = false) = {
    val data = io.Source.fromFile(file).getLines.toList.tail
    
    //Logger.debug(data.mkString("\n"))

    val newPois = data map { g =>
      val geo = (g split """\|""") :+ ""

      def optEmpty(st: String): Option[String] = st match {
        case "" => None
        case s:String => Some(s)
      }

      def opt0(st: String): String = st match {
        case "" => "0"
        case s:String => s
      }

      //Logger.debug(g)

      POI(
        id = Some(BSONObjectID.generate),
        featureId = geo(0) toInt,
        name = geo(1),
        featureClass = geo(2),
        state = geo(3),
        stateId = geo(4) toInt,
        county = geo(5),
        countyId = opt0(geo(6)) toInt,
        loc = GeoPoint(List(geo(10) toDouble, geo(9) toDouble)),
        latitudeDMS = geo(7),
        longitudeDMS = geo(8),
        sourceLatitude = optEmpty(geo(13)).map(_.toDouble),
        sourceLongitude = optEmpty(geo(14)).map(_.toDouble),
        sourceLatitudeDMS = optEmpty(geo(11)),
        sourceLongitudeDMS = optEmpty(geo(12)),
        elevation = opt0(geo(15)) toInt,
        elevationFT = if(missingElevation) 0 else opt0(geo(16)) toInt,
        mapName = if(missingElevation) geo(16) else geo(17),
        dateCreated = if(missingElevation) optEmpty(geo(17)).map(DateTimeFormat.shortDate.parseDateTime) else optEmpty(geo(18)).map(DateTimeFormat.shortDate.parseDateTime),
        dateEdited = if(missingElevation) optEmpty(geo(18)).map(DateTimeFormat.shortDate.parseDateTime) else optEmpty(geo(19)).map(DateTimeFormat.shortDate.parseDateTime)
      )
    }

    Logger.debug(s"inserting ${newPois.length} pois")

    Logger.debug(Json.toJson(newPois.head).toString)

    newPois foreach { p => collection.insert(p) map (le =>
      le.errMsg match {
        case Some(errMsg) => Logger.error(errMsg)
      }
    )}
  }

  def importToAll(file: File) = importPOI(file, pois)
  def importToConcise(file: File) = importPOI(file, concisePois, true)

}

