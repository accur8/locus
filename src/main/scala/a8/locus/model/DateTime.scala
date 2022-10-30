package a8.locus.model

import a8.shared.json.ast.JsStr
import a8.shared.json.{JsonCodec, JsonTypedCodec}

import java.time.{LocalDateTime, Month, ZoneId, ZoneOffset}
import java.util.GregorianCalendar


object DateTime {

  val empty: DateTime =
    apply(0L)

  implicit val format: JsonTypedCodec[DateTime,JsStr] =
    JsonTypedCodec.string.dimap[DateTime] (
      uberParse,
      _.toString,
    )

  def apply(value: java.util.Date): DateTime = {
    val ldt =
      value
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    DateTime.apply(
      ldt.getYear,
      ldt.getMonth,
      ldt.getDayOfMonth,
      ldt.getHour,
      ldt.getMinute,
      ldt.getSecond,
    )
  }

  def apply(epoc: Long): DateTime =
    apply(new java.util.Date(epoc))

  def apply(
    year: Int,
    month: Month,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
  ): DateTime = {
    DateTime(
      LocalDateTime.of(year, month, day, hour, minute, second)
    )
  }

  implicit val ordering: Ordering[DateTime] =
    Ordering.by[DateTime,Long](_.epoc)

  def uberParse(value: String): DateTime = ???

}

case class DateTime(value: LocalDateTime) {

  val year = value.getYear
  val month: Month = value.getMonth
  val day: Int = value.getDayOfMonth
  val hour: Int = value.getMinute
  val minute: Int = value.getMinute
  val second: Int = value.getSecond

  lazy val epoc: Long =
    value.toEpochSecond(ZoneOffset.UTC)

  override def toString: String =
    value.toString

}
