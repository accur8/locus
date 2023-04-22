package a8.locus.ziohttp


import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel
import zio.{Chunk, ZIO}
import zio.http.{Body, Header, Headers, MediaType, Method, Request, Response, Status, URL}
import a8.locus.SharedImports.*
import a8.shared.FileSystem
import io.accur8.neodeploy.resolvedmodel.ResolvedRepository
import zio.http.Header.{ContentType, HeaderType}

import java.io.ByteArrayInputStream

object model {

  type HttpStatusCode = zio.http.Status

  object HttpStatusCode {

    val Ok = Status.Ok
    val MovedPermanently = Status.MovedPermanently
    val NotAuthorized = Status.Unauthorized
    val Forbidden = Status.Forbidden
    val NotFound = Status.NotFound
    val MethodNotAllowed = Status.MethodNotAllowed
    val Conflict = Status.Conflict
    val Error = Status.InternalServerError

  }


  case class PreparedRequest(
    requestMeta: RequestMeta,
    responseEffect: Request => ZIO[Env, Throwable, Response],
  )


  case class RequestMeta(
    curlLogRequestBody: Boolean
  )

  case class PreFlightRequest(
    method: zio.http.Method,
    path: FullPath,
  )

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

  type Env = zio.Scope & ResolvedModel & UserService

  type M[A] = zio.ZIO[Env, Throwable,A]

  def htmlResponse(htmlContent: String): HttpResponse =
    HttpResponse
      .fromHtml(htmlContent)

  def htmlResponseZ(htmlContent: String): zio.UIO[HttpResponse] =
    zsucceed(htmlResponse(htmlContent))

//  type HttpResponseBody = zio.http.Body

  object ContentTypes {

    val empty = ContentType(MediaType("", ""))
    val html = ContentType.parse("text/html").toOption.get
    val xml = ContentType.parse("text/xml").toOption.get

  }

  object HttpResponseBody {

    def fromStr(string: String, contentType: ContentType = ContentTypes.empty): HttpResponse =
      HttpResponse(
        body = Body.fromString(string),
        headers = Headers(contentType)
      )

    def fromBytes(bytes: Array[Byte], contentType: ContentType = ContentTypes.empty): HttpResponse =
      HttpResponse(
        body = Body.fromChunk(Chunk.fromArray(bytes)),
        headers = Headers(contentType)
      )

    def fromFile(file: FileSystem.File, contentType: ContentType = ContentTypes.empty): HttpResponse =
      HttpResponse(
        body = Body.fromFile(file.asNioPath.toFile),
        headers = Headers(contentType)
      )

    def html(html: String) = fromStr(html, ContentTypes.html)
    def xml(xml: String) = fromStr(xml, ContentTypes.xml)

  }

  type HttpResponse = zio.http.Response

  object HttpResponse {

    val Ok = HttpResponses.Ok()
    val OkZ = zsucceed(emptyResponse)

    def apply(
      status: Status = Status.Ok,
      body: Body = Body.empty,
      headers: Headers = Headers.empty,
    ) =
      Response(
        body = body,
        status = status,
      )

    def emptyResponse(statusCode: HttpStatusCode): HttpResponse =
      HttpResponse(
        status = statusCode,
      )

    def methodNotAllowed(message: String = ""): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.MethodNotAllowed)

    def forbidden(message: String = ""): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.Forbidden)

    def notFound(message: String = ""): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.NotFound)

    def fromFile(file: FileSystem.File, contentType: ContentType=ContentTypes.empty): HttpResponse =
      HttpResponse(body=Body.fromFile(file.asNioPath.toFile), headers=Headers(contentType))

    def fromHtml(html: String): HttpResponse =
      HttpResponse(body=Body.fromString(html), headers=Headers(ContentTypes.html))

    def error(message: String): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.NotFound)

    def errorz(message: String): zio.UIO[HttpResponse] =
      zsucceed(error(message))

    def permanentRedirect(location: UrlPath): HttpResponse =
      HttpResponse(
        headers = Headers(Header.Location(URL(location.zioPath))),
        status = HttpStatusCode.MovedPermanently,
      )

  }

}
