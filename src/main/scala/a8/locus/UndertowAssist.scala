package a8.locus


import java.io.{ByteArrayInputStream, FileInputStream}
import a8.locus.Dsl.UrlPath
import a8.shared.FileSystem.File
import io.undertow.server.HttpServerExchange
import scala.jdk.CollectionConverters._
import SharedImports._

object UndertowAssist {

  implicit class ExchangeOps(exchange: HttpServerExchange) {

    def setStatusCode(statusCode: HttpStatusCode): ExchangeOps =
      exchange.setStatusCode(statusCode.number)

    def headerValue(name: String): Option[String] =
      Option(exchange.getRequestHeaders.get(name))
        .flatMap(_.iterator().asScala.nextOption())

    def headerValues(name: String): Iterable[String] =
      Option(exchange.getRequestHeaders.get(name))
        .toList
        .flatMap(_.iterator().asScala)

  }

  object HttpResponseBody {

    val empty = fromBytes(Array[Byte](), ContentType.empty)

    def fromStr(string: String, contentType: ContentType = ContentType.empty): HttpResponseBody =
      fromInputStreamFn(() => new ByteArrayInputStream(string.getBytes), contentType)

    def fromBytes(bytes: Array[Byte], contentType: ContentType = ContentType.empty): HttpResponseBody =
      fromInputStreamFn(() => new ByteArrayInputStream(bytes), contentType)

    def fromFile(file: File, contentType: ContentType = ContentType.empty): HttpResponseBody =
      fromInputStreamFn(() => new FileInputStream(file.canonicalPath), contentType)

    def fromInputStreamFn(fn: ()=>java.io.InputStream, contentType: ContentType = ContentType.empty): HttpResponseBody = {
      val ct = contentType
      new HttpResponseBody {
        override def contentType: ContentType = ct
        override protected def openInputStream: java.io.InputStream = fn()
      }
    }

    def html(html: String) = fromStr(html, ContentType.html)
    def xml(xml: String) = fromStr(xml, ContentType.xml)

  }

  trait HttpResponseBody {
    def asOkResponse = HttpResponse(this, HttpStatusCode.Ok)
    def contentType: ContentType = ContentType.empty
    protected def openInputStream: java.io.InputStream
    def withInputStream[A](fn: java.io.InputStream=>A): A = {
      val input = openInputStream
      try {
        fn(input)
      } finally {
        input.close()
      }
    }

  }

  case class HttpResponse(
    content: HttpResponseBody,
    statusCode: HttpStatusCode = HttpStatusCode.Ok,
    headers: Map[HttpHeader,String] = Map(),
  )

  object HttpResponse {

    val Ok = HttpResponse(HttpResponseBody.empty)
    val OkZ = zsucceed(emptyResponse)

    def emptyResponse(statusCode: HttpStatusCode): HttpResponse =
      HttpResponse(
        content = HttpResponseBody.empty,
        statusCode = statusCode,
      )

    def methodNotAllowed(message: String = ""): HttpResponse =
      HttpResponse(HttpResponseBody.fromStr(message), HttpStatusCode.MethodNotAllowed)

    def forbidden(message: String = ""): HttpResponse =
      HttpResponse(HttpResponseBody.fromStr(message), HttpStatusCode.Forbidden)

    def notFound(message: String = ""): HttpResponse =
      HttpResponse(HttpResponseBody.fromStr(message), HttpStatusCode.NotFound)

    def fromFile(file: File, contentType: ContentType = ContentType.empty): HttpResponse =
      HttpResponse(HttpResponseBody.fromFile(file, contentType))

    def fromHtml(html: String): HttpResponse =
      HttpResponse(HttpResponseBody.html(html))

    def error(message: String): HttpResponse =
      HttpResponse(HttpResponseBody.fromStr(message), HttpStatusCode.NotFound)

    def errorz(message: String): zio.UIO[HttpResponse] =
      zsucceed(error(message))

    def permanentRedirect(location: UrlPath): HttpResponse =
      HttpResponse(
        headers = Map(HttpHeader.Location -> ("/" + location.toString)),
        content = HttpResponseBody.empty,
        statusCode = HttpStatusCode.MovedPermanently,
      )

  }

  object ContentType {
    val empty = ContentType(None)
    val html = ContentType("text/html")
    val xml = ContentType("application/xml")
    def apply(contentType: String): ContentType = ContentType(Some(contentType))
  }
  case class ContentType(value: Option[String])

}
