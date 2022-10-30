package a8.locus

import io.undertow.util.{HttpString, StatusCodes}

case class HttpHeader(value: String) {
  val httpString = new HttpString(value)
}

object HttpHeader {

  val Location = HttpHeader("Location")
  val WWWAuthenticate = HttpHeader("WWW-Authenticate")

}
