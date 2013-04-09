package models 

import play.api.mvc.PathBindable
import play.api.libs.json._
import play.api.data.validation.ValidationError

import scala.util.control.Exception.allCatch

import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat

object DateTime {

  implicit def dateBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[JodaDateTime] {

    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

    def bind(key: String, value: String): Either[String, JodaDateTime] =
      for {
        dateString <- stringBinder.bind(key, value).right
        date <- (allCatch opt { formatter.parseDateTime(dateString) }).toRight("Date not in valid format").right
      } yield date

    def unbind(key: String, date: JodaDateTime): String =
      stringBinder.unbind(key, date.toString)

  }

  // FlightAware gives us seconds since epoch, we need to x1000 that so it's in milliseconds for Joda
  implicit val SecondsJodaDateReads = new Reads[JodaDateTime] {

    def reads(json: JsValue): JsResult[JodaDateTime] = json match {
      case JsNumber(d) => JsSuccess(new JodaDateTime(d.toLong*1000))
      case _           => JsError(Seq(JsPath() -> Seq(ValidationError("expected seconds"))))
    }

  }


}
