package a8.locus.ziohttp


import a8.locus.{ResolvedModel, UndertowAssist, ziohttp}
import a8.shared.app.{BootstrappedIOApp, LoggingF}
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import zio.{Chunk, Task, ZIO}
import zio.http.HttpError.{BadRequest, InternalServerError}
import zio.http.Method.{GET, POST}
import zio.http.{Body, Headers, Http, HttpApp, HttpError, Method, Request, Response, Server, Status, URL, Version}

import java.net.InetAddress
import ZHttpHandler.{HttpResponseException, Path}
import a8.locus.SharedImports.*
import a8.locus.ziohttp.Main.Config
import Router.{PreFlightRequest, RequestMeta}

object Main extends BootstrappedIOApp {

  type Env = Any

  lazy val httpPort = 8080

  case class Config(
    protocolForCurlLogging: String,
  )

  override def runT: ZIO[BootstrapEnv, Throwable, Unit] = ???

}

object Router {

  case class RequestMeta(
    curlLogRequestBody: Boolean
  )

  case class PreFlightRequest(
    method: zio.http.Method,
    path: ZHttpHandler.FullPath,
  )

}

case class Router(
  config: Config,
  resolvedModel: ResolvedModel,
)
  extends LoggingF
{

  import ZHttpHandler.Env

  def runT: ZIO[BootstrapEnv, Throwable, Unit] =
    ???
//    Server.start[Env](
//      InetAddress.getByName("0.0.0.0"),
//      httpPort,
//      routes,
//    )

  lazy val repoHandlers =
    resolvedModel
      .resolvedProxyPaths
      .map { resolvedRepo =>
        RepoHttpHandler(resolvedModel, resolvedRepo)
      }

  val handlers = repoHandlers ++ Seq(ListReposHandler, RootHandler)


  case class PreparedRequest(
    requestMeta: RequestMeta,
    responseEffect: Request=>ZIO[Env, Throwable, Response],
  )

  def prepareRequest(preFlightRequest: PreFlightRequest): PreparedRequest =
    handlers
      .find(_.matcher.matches(preFlightRequest))
      .map { handler =>
        val responseFn: Request=>ZIO[Env, Throwable, Response] =
          { req =>
            handler
              .respond(req)
              .map(toZHttpResponse)
          }
        PreparedRequest(
          handler.preFlightRequestMeta(preFlightRequest),
          responseFn,
        )
      }
      .getOrElse(
        PreparedRequest(
          RequestMeta(true),
          { req => notFound(req) },
        )
      )

  def notFound(request: Request): ZIO[Env, Throwable, Response] = ??? //HttpResponses.NotFound(s"no match found for request ${rawRequest.url.encode} with http method ${rawRequest.method}")

  def toZHttpResponse(response: UndertowAssist.HttpResponse): zio.http.Response =
    ???

  lazy val routes: HttpApp[Env, Throwable] =
    Http.collectZIO[Request] { rawRequest =>
      val preFlightRequest = PreFlightRequest(rawRequest.method, Path.fromzioPath(rawRequest.path))
      val context = s"${rawRequest.method} ${rawRequest.url.encode}"
      val preparedRequest = prepareRequest(preFlightRequest)
      val effect: ZIO[Env, Throwable, Response] =
        for {
          // some dancing here since curl will consume the request body
          wrappedRequest <- curl(rawRequest, preparedRequest.requestMeta.curlLogRequestBody)
          _ <- loggerF.debug(s"curl for request\n${wrappedRequest._1.indent("    ")}")
          responseEffect = preparedRequest.responseEffect(wrappedRequest._2)
          response <-
            responseEffect
              .uninterruptible
              .either
              .flatMap {
                case Left(HttpResponseException(httpResponse)) =>
                  zsucceed(toZHttpResponse(httpResponse))
                case Left(httpError: HttpError) =>
                  loggerF.warn(s"Error servicing request: ${context}, responding with ${httpError}") *>
                    HttpResponses.fromError(httpError)
                case Left(th) =>
                  loggerF.error(s"Error servicing request: ${context}", th) *>
                    HttpResponses.text(th.stackTraceAsString, status = Status.InternalServerError)
                case Right(s) =>
                  zsucceed(s)
              }
          _ <- loggerF.debug(s"completed processing ${response.status.code} -- ${context}")
        } yield response

      effect.correlateWith(context)

    }


  def curl(request: Request, logRequestBody: Boolean): Task[(String,Request)] = {
    if ( logRequestBody ) {
      curlForRequest(request)
    } else {
      curlForRequestNoBody(request)
    }
  }


  def curlForRequestNoBody(request: Request): Task[(String,Request)] = {

    val curl: String = {
      //          val requestBodyStr = new String(requestBodyByteBuf.array())
      val initialLines: Chunk[String] = Chunk("curl", s"-X ${request.method}")
      val headerLines: Chunk[String] = request.headers.map(h => s"-H '${h.headerName}: ${h.renderedValue}'").toChunk
      val url: Chunk[String] = Chunk(s"${config.protocolForCurlLogging}://${request.rawHeader("Host").getOrElse("nohost")}${request.url.encode}")
      (initialLines ++ headerLines ++ url)
        .mkString(" \\\n    ")
    }

    ZIO.succeed(curl -> request)

  }

  def curlForRequest(request: Request): Task[(String,Request)] = {

    def impl(requestBodyStr: Option[String]): (String,Request) = {
      val curl: String = {
        //          val requestBodyStr = new String(requestBodyByteBuf.array())
        val initialLines: Chunk[String] = Chunk("curl", s"-X ${request.method}")
        val headerLines: Chunk[String] = request.headers.filterNot(_.headerName.toLowerCase === "host").map(h => s"-H '${h.headerName}: ${h.renderedValue}'").toChunk
        val url: Chunk[String] = Chunk(s"'${config.protocolForCurlLogging}://${request.rawHeader("Host").getOrElse("nohost")}${request.url.encode}'")
        val requestBody = Chunk.fromIterable(requestBodyStr.map(rbs => s"--data '${rbs}'"))
        (initialLines ++ headerLines ++ url ++ requestBody)
          .mkString(" \\\n    ")
      }

      val newData =
        requestBodyStr match {
          case Some(rbs) =>
            Body.fromString(rbs)
          case None =>
            Body.empty
        }

      curl -> request.copy(body = newData)

    }

    request
      .body
      .asString
      .map {
        case bodyStr if bodyStr.isEmpty =>
          impl(None)
        case bodyStr =>
          impl(bodyStr.some)
      }
  }
  
}
