package a8.locus.ziohttp

import a8.locus.SharedImports.CiString
import zio.http.{Request, Response}
import a8.locus.{ResolvedModel, UndertowAssist}
import a8.locus.UndertowAssist.HttpResponse
import zio.http.Method
import io.accur8.neodeploy.resolvedmodel.ResolvedRepository
import ZHttpHandler.*
import a8.locus.SharedImports.*
import a8.locus.ziohttp.Router.{PreFlightRequest, RequestMeta}

object ZHttpHandler {

  case class HttpResponseException(httpResponse: HttpResponse) extends Exception

  type Request = zio.http.Request

  object Path {
    def fromzioPath(zioPath: zio.http.Path): FullPath = {
      val isDirectory =
        zioPath
          .segments
          .lastOption
          .collect {
            case zio.http.Path.Segment.Root =>
              ()
          }
          .nonEmpty
      val parts =
        zioPath
          .segments
          .collect {
            case zio.http.Path.Segment.Text(s) =>
              CiString(s)
          }
      FullPath(parts.toIndexedSeq)
    }

  }

  sealed trait Path {
    val parts: IndexedSeq[CiString]
    def matches(path: FullPath): Boolean
  }

  object PathPrefix {
    def apply(parts: (String | CiString)*): PathPrefix =
      PathPrefix(
        parts
          .map {
            case s: CiString =>
              s
            case s: String =>
              CiString(s)
          }
          .toIndexedSeq
      )
  }

  case class PathPrefix(parts: IndexedSeq[CiString]) extends Path {
    override def matches(path: FullPath): Boolean =
      path.parts.startsWith(parts)
  }
  case class FullPath(parts: IndexedSeq[CiString]) extends Path {
    override def matches(path: FullPath): Boolean =
      parts == path.parts
  }

  case class RequestMatcher(methods: Seq[Method], paths: Seq[Path]) {

    lazy val methodsSet = methods.toSet

    def matches(preFlightRequest: PreFlightRequest): Boolean = {
      if ( methodsSet.contains(preFlightRequest.method) ) {
        paths.exists(_.matches(preFlightRequest.path))
      } else {
        false
      }
    }

  }

  type Env = zio.Scope & ResolvedModel & ResolvedRepository & UserService

  type M[A] = zio.ZIO[Env, Throwable,A]

  def htmlResponse(htmlContent: String): HttpResponse =
    UndertowAssist
      .HttpResponse
      .fromHtml(htmlContent)

  def htmlResponseZ(htmlContent: String): zio.UIO[HttpResponse] =
    zsucceed(htmlResponse(htmlContent))

}

trait ZHttpHandler {

  lazy val matcher: RequestMatcher
  def respond(req: Request): M[HttpResponse]

  /**
    * a pre-flight change to determine how the router should handle this request (like what level of curl logging)
    */
  def preFlightRequestMeta(req: PreFlightRequest) =
    RequestMeta(req.method != Method.PUT)

}
