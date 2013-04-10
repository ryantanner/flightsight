package models

import play.api.Play.current

import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection.JSONCollection

import reactivemongo.api._
import scala.concurrent.ExecutionContext.Implicits.global

trait Mongo extends MongoController {

  val db = ReactiveMongoPlugin.db

  implicit val airlineColl = db.collection[JSONCollection]("airlines")
  implicit val airportColl = db.collection[JSONCollection]("airports")

}
