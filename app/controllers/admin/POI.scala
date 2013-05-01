package controllers.admin

import play.api._
import play.api.Logger
import play.api.mvc._
import play.api.mvc.Results._
import play.api.mvc.Security
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

import java.io.{File, FileInputStream}

import models._

object POI extends Controller with Secured {

  val queryForm = Form(
    mapping(
      "long" -> of[Double],
      "lat" -> of[Double] 
    )(GeoPoint.latlng)(GeoPoint.unlatlng)
  )

  val uploadForm = Form(
    "collection" -> text
  )

  def upload = IsAuthenticated { username => _ =>
    Ok(views.html.admin.uploadPOI(username)(uploadForm))
  }

  def processGNIS = Action(parse.multipartFormData) { implicit request =>
    uploadForm.bindFromRequest.fold(
      frmWErr => BadRequest("invalid collection"),
      collectionName => {
        request.body.files foreach { file =>
          val filename = file.filename
          val contentType = file.contentType
          val fileLength = file.ref.file.length

          Logger.info(s"Processing $filename $fileLength bytes")

          val dest = new File("/tmp/" + filename)

          file.ref.moveTo(dest, replace = true)

          collectionName match {
            case "all" => models.POI.importToAll(dest)
            case "concise" => models.POI.importToConcise(dest)
          }
        }

        Redirect(routes.Admin.index).flashing(
          "success" -> ("Uploaded poi files")
        )
      }
    )
  }

  def query = IsAuthenticated { username => _ =>
    Ok(views.html.admin.poiQuery(username)(queryForm))
  }

  def queryPoint = IsAuthenticated { username => implicit request =>
    queryForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.poiQuery(username)(formWithErrors)),
      value => Async {
        val futurePoints = models.POI.featuresNear(value)

        futurePoints map { case (concise, all) =>
          Ok(views.html.admin.poiList(username)(concise, all))
        }
      }
    )
  }

}
