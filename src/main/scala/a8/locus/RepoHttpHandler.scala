package a8.locus


import java.nio.ByteBuffer
import io.undertow.server.{HttpHandler, HttpServerExchange}
import a8.locus.Config.{User, UserPrivilege}
import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.RepoContent.TempFile
import a8.locus.ResolvedModel.{ResolvedContent, ResolvedRepo}
import a8.locus.UndertowAssist.HttpResponse
import io.undertow.util.{Headers, StatusCodes}
import org.slf4j.MDC
import SharedImports._
import a8.shared.app.Logging

case class RepoHttpHandler(controller: ResolvedModel, resolvedRepo: ResolvedRepo) extends HttpHandler with Logging {

  import UndertowAssist._

  case class Request(
    exchange: HttpServerExchange,
    user: User,
    urlPath: UrlPath,
  )

  lazy val methods: Map[CiString,Request=>HttpResponse] =
    Iterable(
      "GET" -> doGet _,
      "PUT" -> doPut _,
      "HEAD" -> doHead _,
    )
      .map(t => CiString(t._1) -> t._2)
      .toMap

  def resolveUser(exchange: HttpServerExchange): Either[HttpResponse,User] = {

    def requireAuthenticationResponse =
      HttpResponse(
        content = HttpResponseBody.empty,
        statusCode = HttpStatusCode.NotAuthorized,
        headers = Map(
          HttpHeader.WWWAuthenticate -> s"""Basic realm="${controller.config.realm}""""
        )
      )

    Option(exchange.getRequestHeaders.get("Authorization"))
        .flatMap { headerValues =>
          headerValues.iterator().asScala.flatMap(authenticate).nextOption()
        }
        .map(Right.apply)
        .getOrElse(Left(requireAuthenticationResponse))

  }

  def authenticate(authenticateHeaderValue: String): Option[User] = {

    authenticateHeaderValue.trim.splitList(" ", limit = 2) match {
      case List("Basic", credentialsBase64) =>
        val credentials = new String(java.util.Base64.getDecoder.decode(credentialsBase64))
        credentials.splitList(":", limit = 2) match {
          case List(user, password) =>
            controller
              .config
              .users
              .find(u => u.name =:= user && u.password =:= password)
          case _ =>
            None
        }
      case _ =>
        None
    }


  }


  def handleRequest(exchange: HttpServerExchange): Unit = {

    if (exchange.isInIoThread()) {
      if ( methods.contains(CiString(exchange.getRequestMethod.toString)) ) {
        exchange.dispatch(this)
      } else {
        exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED)
        exchange.endExchange()
      }
    } else {
      exchange.startBlocking()

      MDC.put("url", exchange.getRequestURL)

      val urlPath = UrlPath.parse(exchange.getRelativePath)

      logger.debug(s"start ${exchange.getRequestMethod} ${exchange.getRequestURL}")

      try {

        val response =
          resolveUser(exchange) match {
            case Left(httpResponse) =>
              httpResponse
            case Right(user) =>
              val request = Request(exchange, user, urlPath)
              val methodHandler = methods(CiString(exchange.getRequestMethod.toString))
              methodHandler(request)
          }

        exchange.setStatusCode(response.statusCode)
        response.headers.foreach( header =>
          exchange.getResponseHeaders.add(header._1.httpString, header._2)
        )
        response.content.contentType.value.foreach(exchange.getResponseHeaders.add(Headers.CONTENT_TYPE, _))
        response.content.withInputStream{ input =>
          val output = exchange.getOutputStream
          pipe(input, output)
        }

        logger.debug(s"completed ${exchange.getRequestMethod} ${response.statusCode.number} ${exchange.getRequestURL} ")

      } catch {
        case e: Exception =>
          exchange.setStatusCode(500)
          val msg = s"error processing ${exchange.getRequestURL}\n${e.stackTraceAsString}"
          exchange.getResponseSender.send(msg)
          logger.error(msg)

      } finally {
        exchange.endExchange()
        MDC.clear()
      }

    }
  }

  def doPut(request: Request): HttpResponse = {
    if ( request.user.privilege === UserPrivilege.Read ) {
      HttpResponse.forbidden()
    } else {
      import request._
      val urlPath = UrlPath.parse(exchange.getRelativePath)
      val status =
        controller.withWorkDirectory { dir =>
          val tempFile = dir.file(urlPath.last)
          val input = exchange.getInputStream
          tempFile.withOutputStream { output =>
            pipe(input, output)
          }
          resolvedRepo.put(urlPath, tempFile)
        }
      HttpResponse.emptyResponse
    }
  }

  def doHead(request: Request): HttpResponse = {
    import request._
    resolveContent(exchange) match {
      case Some(_) =>
        HttpResponse.emptyResponse
      case None =>
        HttpResponse.notFound()
    }
  }

  def resolveContent(exchange: HttpServerExchange): Option[ResolvedContent] = {
    val urlPath = UrlPath.parse(exchange.getRelativePath)
    resolvedRepo.resolveContent(urlPath)
  }

  def doGet(request: Request): HttpResponse = {
    import request._
    import request._
    resolveContent(exchange) match {
      case Some(resolvedContent) =>
        logger.debug(s"resolved content using ${resolvedContent.repo.name}  fromCache = ${resolvedContent.fromCache}  ${resolvedContent.content}")
        import a8.locus.ResolvedModel.RepoContent._
        resolvedContent.content match {
          case CacheFile(file) =>
            HttpResponse.fromFile(file)
          case TempFile(file) =>
            HttpResponse.fromFile(file)
          case Generated(responseBody) =>
            responseBody.asOkResponse
          case Redirect(path) =>
            val rootPath = UrlPath.parse(request.exchange.getResolvedPath)
            HttpResponse.permanentRedirect(rootPath.append(path))
        }
      case None =>
        HttpResponse.notFound(s"unable to resolve ${urlPath}")
    }

  }

}
