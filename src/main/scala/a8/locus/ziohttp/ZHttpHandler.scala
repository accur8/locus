package a8.locus.ziohttp

import a8.locus.SharedImports.CiString
import zio.http.{Request, Response}
import a8.locus.ResolvedModel
import zio.http.Method
import io.accur8.neodeploy.resolvedmodel.ResolvedRepository
import a8.locus.SharedImports.*
import model._

trait ZHttpHandler {

  lazy val matcher: RequestMatcher
  def respond(req: Request): M[HttpResponse]

  /**
    * a pre-flight change to determine how the router should handle this request (like what level of curl logging)
    */
  def preFlightRequestMeta(req: PreFlightRequest) =
    RequestMeta(req.method != Method.PUT)

}
