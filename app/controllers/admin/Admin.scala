package controllers.admin

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.mvc.Security
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

import models._

object Admin extends Controller with Secured {

  def index = IsAuthenticated { username => implicit request =>
    Ok(views.html.admin.index(username))
  }

    // -- Authentication

  val loginForm = Form(
    tuple(
      "email" -> text,
      "password" -> text
    ) verifying ("Invalid email or password", result => result match {
      case (email, password) => Await.result(User.authenticate(email, password).map(_.isDefined), 1 second)
    })
  )

  /**
   * Login page.
   */
  def login = Action { implicit request =>
    Ok(views.html.admin.login(loginForm))
  }

  /**
   * Handle login form submission.
   */
  def authenticate = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.login(formWithErrors)),
      user => Redirect(routes.Admin.index).withSession("email" -> user._1)
    )
  }

  /**
   * Logout and clean the session.
   */
  def logout = Action {
    Redirect(routes.Admin.login).withNewSession.flashing(
      "success" -> "You've been logged out"
    )
  }

}




/**
 * Provide security features
 */
trait Secured {
  
  /**
   * Retrieve the connected user email.
   */
  private def username(request: RequestHeader) = request.session.get("email")

  /**
   * Redirect to login if the user in not authorized.
   */
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Admin.login)
  
  // --
  
  /** 
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) = Security.Authenticated(username, onUnauthorized) { user =>
    Action(request => f(user)(request))
  }

}
