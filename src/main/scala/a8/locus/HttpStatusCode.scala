package a8.locus

import io.undertow.util.StatusCodes

sealed abstract class HttpStatusCode(val number: Int) extends enumeratum.EnumEntry {
  lazy val reason = StatusCodes.getReason(number)
}

object HttpStatusCode {

  case object Ok extends HttpStatusCode(200)
  case object MovedPermanently extends HttpStatusCode(301)
  case object NotAuthorized extends HttpStatusCode(401)
  case object Forbidden extends HttpStatusCode(403)
  case object NotFound extends HttpStatusCode(404)
  case object MethodNotAllowed extends HttpStatusCode(405)
  case object Conflict extends HttpStatusCode(409)
  case object Error extends HttpStatusCode(500)

}
