package controllers

import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import play.api.data._
import play.api.Logger

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._


import org.joda.time.{DateTime => JodaDateTime}

import models._
//import models.transformers._

object Airlines extends Controller {

  import Airline._

  def listAll = Action {
    val futureAirlines = Airline.all
    Async {
      futureAirlines.map(al => Ok(Json.arr(al)))
    }
  }

}
