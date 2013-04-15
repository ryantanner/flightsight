package models

import play.api.mvc.PathBindable
import play.api.libs.json._
import play.api.data.validation.ValidationError

import scala.util.control.Exception.allCatch

import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTimeZone => JodaTimeZone}
import org.joda.time.Period

object DateTime {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  implicit def dateBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[JodaDateTime] {

    def bind(key: String, value: String): Either[String, JodaDateTime] =
      for {
        dateString <- stringBinder.bind(key, value).right
        date <- (allCatch opt { formatter.parseDateTime(dateString) }).toRight("Date not in valid format").right
      } yield date

    def unbind(key: String, date: JodaDateTime): String =
      stringBinder.unbind(key, formatter.print(date))

  }

  // FlightAware gives us seconds since epoch, we need to x1000 that so it's in milliseconds for Joda
  // If a flight is in the future, actual times are 0 so we'll store a None
  def secondsOptionalJodaDateReads = new Reads[Option[JodaDateTime]] {

    def reads(json: JsValue): JsResult[Option[JodaDateTime]] = json match {
      case JsNumber(d) if (d > 0) => JsSuccess(Some(new JodaDateTime(d.toLong*1000, JodaTimeZone.UTC)))
      case JsNumber(d) if (d <= 0) => JsSuccess(None)
      case _           => JsError(Seq(JsPath() -> Seq(ValidationError("expected seconds"))))
    }

  }

  // FlightAware gives us seconds since epoch, we need to x1000 that so it's in milliseconds for Joda
  def secondsJodaDateReads = new Reads[JodaDateTime] {

    def reads(json: JsValue): JsResult[JodaDateTime] = json match {
      case JsNumber(d) => JsSuccess(new JodaDateTime(d.toLong*1000, JodaTimeZone.UTC))
      case _           => JsError(Seq(JsPath() -> Seq(ValidationError("expected seconds"))))
    }

  }

  implicit val periodReads = new Reads[Period] {

    def reads(json: JsValue): JsResult[Period] = json match {
      case JsString(s) => 
        val times = s.split(":").map(_.toInt)
        JsSuccess(new Period(times(0), times(1), times(2), 0))
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("expected string"))))
    }

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
