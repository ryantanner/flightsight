package models

import play.api.Play.current

import play.api.libs.json._
import play.api.libs.json.util._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.BSONFormats._
import play.modules.reactivemongo.json.collection._

import org.mindrot.jbcrypt.BCrypt

case class User(
  id: Option[BSONObjectID],
  email: String, 
  name: String, 
  password: String)

object User {

  val db = ReactiveMongoPlugin.db
  implicit val userFormat = Json.format[User]

  val users = db.collection[JSONCollection]("users")
  
  // -- Queries
  
  /**
   * Retrieve a User from email.
   */
  def findByEmail(email: String): Future[Option[User]] = {
    users.find(Json.obj("email" -> email)).cursor[User].headOption
  }
  
  /**
   * Retrieve all users.
   */
  def all: Future[Seq[User]] = {
    users.find(Json.obj()).cursor[User].toList
  }
  
  /**
   * Authenticate a User.
   */
  def authenticate(email: String, password: String): Future[Option[User]] = {
    users.find(Json.obj("email" -> email)).cursor[User].headOption map { 
      _ filter {
        user => BCrypt.checkpw(password, user.password)
      }
    }
  }
   
  /**
   * Create a User.
   */
  def create(user: User): Future[User] = {
    users.insert(user.copy(password = BCrypt.hashpw(user.password, BCrypt.gensalt()))) flatMap { _ =>
      User.findByEmail(user.email).map(_.get)
    }

  }
  
}

