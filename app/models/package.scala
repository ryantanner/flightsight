package models 

import play.api.mvc.PathBindable
import play.api.libs.json._
import play.api.data.validation.ValidationError

import scala.util.control.Exception.allCatch

import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTimeZone => JodaTimeZone}

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

}

object DateTimeZone {

  /**
   * Reads for the `org.joda.time.DateTimeZone` type.
   *
   * @param pattern a long TimeZne id, as specified in `java.util.TimeZone`.
   */
  def jodaTimeZoneReads: Reads[JodaTimeZone] = new Reads[JodaTimeZone] {
    def reads(json: JsValue): JsResult[JodaTimeZone] = json match {
      case JsString(s) => try {
          val tzone = JodaTimeZone.forID(s.replace(":",""))
          JsSuccess(tzone)
        } catch {
          case ex: IllegalArgumentException => JsError(Seq(JsPath() -> Seq(ValidationError("validate.error.expected.jodadatetimezone.format"))))
        }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("validate.error.expected.date"))))
    }
  }

  def jodaTimeZoneWrites: Writes[JodaTimeZone] = new Writes[JodaTimeZone] {
    def writes(tz: JodaTimeZone): JsValue = {
      JsString(tz.getID)
    }
  }

  implicit val jodaTimeZoneFormat: Format[JodaTimeZone] = Format(jodaTimeZoneReads, jodaTimeZoneWrites)

}
