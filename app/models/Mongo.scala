package models

import reactivemongo.api._
import scala.concurrent.ExecutionContext.Implicits.global

trait Mongo {

  private val connection = MongoConnection(List("localhost:27017"))
  private val db = connection("plugin")
  implicit val collection = db("acoll")

}
