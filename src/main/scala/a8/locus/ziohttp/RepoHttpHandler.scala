package a8.locus.ziohttp


import a8.locus.Config.{Subnet, SubnetManager, User, UserPrivilege}
import a8.locus.Dsl.UrlPath
import a8.locus.{ResolvedModel, UndertowAssist}
import a8.locus.ResolvedModel.RepoContent.TempFile
import a8.locus.ResolvedModel.{ContentPath, ResolvedContent, ResolvedRepo}
import a8.locus.Routing.Router
import a8.locus.UndertowAssist.HttpResponse
import a8.shared.app.Logging
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, StatusCodes}
import org.apache.commons.net.util.SubnetUtils
import org.slf4j.MDC

import java.nio.ByteBuffer
import a8.locus.ziohttp.ZHttpHandler.*
import a8.locus.SharedImports.*
import zio.http.Method

object RepoHttpHandler {

}

case class RepoHttpHandler(resolvedModel: ResolvedModel, resolvedRepo: ResolvedRepo) extends ZHttpHandler with Logging {

  case class RequestParms(
    urlPath: UrlPath,
  )

  lazy val basePath = PathPrefix("repos", resolvedRepo.name)

  override lazy val matcher: RequestMatcher =
    RequestMatcher(
      Seq(Method.GET, Method.PUT, Method.HEAD),
      Seq(basePath),
    )

  case class MethodHandler(
    method: Method,
    requiredPrivilege: UserPrivilege,
    handler: (Request,ContentPath)=>M[HttpResponse],
  )

  lazy val methodHandlers: Map[Method,MethodHandler] =
    Seq(
      MethodHandler(Method.GET, UserPrivilege.Read, doGet(_,_)),
      MethodHandler(Method.PUT, UserPrivilege.Write, doPut(_,_)),
      MethodHandler(Method.HEAD, UserPrivilege.Read, doHead(_,_)),
    ).toMapTransform(_.method)


  override def respond(req: Request): M[HttpResponse] =
    methodHandlers.get(req.method) match {
      case None =>
        zsucceed(UndertowAssist.HttpResponse.methodNotAllowed("" + req.method + " is not allowed"))
      case Some(mh) =>
        val fullPath = UrlPath.fromZHttpPath(req.path)
        fullPath.contentPath(basePath) match {
          case Some(contentPath) =>
            for {
              userService <- zservice[UserService]
              _ <- userService.requirePrivilege(mh.requiredPrivilege)
              response <- mh.handler(req, contentPath)
            } yield response
          case None =>
            UndertowAssist.HttpResponse.errorz(s"mismatched paths ${fullPath} and ${basePath}.  This should not happen.")
        }
    }

  def doPut(request: Request, path: ContentPath): M[HttpResponse] =
    resolvedModel.withWorkDirectoryM { dir =>
      val tempFile = dir.file(path.parts.last)
      request
        .body
        .asStream
        .run(zio.stream.ZSink.fromFile(tempFile.asJioFile))
        .flatMap { _ =>
          resolvedRepo.putM(path, tempFile)
        }
        .map { sc =>
          HttpResponse.emptyResponse(sc)
        }
    }

  def doHead(request: Request, path: ContentPath): M[HttpResponse] =
    resolveContent(path)
      .map {
        case Some(_) =>
          HttpResponse.Ok
        case None =>
          HttpResponse.notFound()
      }

  def resolveContent(path: ContentPath): M[Option[ResolvedContent]] = {
    resolvedRepo.resolveContentM(path)
  }

  def doGet(request: Request, path: ContentPath): M[HttpResponse] = {
    resolveContent(path)
      .map {
        case Some(resolvedContent) =>
          logger.debug(s"resolved content using ${resolvedContent.repo.name}  fromCache = ${resolvedContent.fromCache}  ${resolvedContent.content}")
          import a8.locus.ResolvedModel.RepoContent.*
          resolvedContent.content match {
            case CacheFile(file) =>
              HttpResponse.fromFile(file)
            case TempFile(file) =>
              HttpResponse.fromFile(file)
            case Generated(responseBody) =>
              responseBody.asOkResponse
            case Redirect(path) =>
              val rootPath = UrlPath.fromZHttpPath(request.path)
              HttpResponse.permanentRedirect(rootPath.append(path))
          }
        case None =>
          HttpResponse.notFound(s"unable to resolve ${request.path}")
      }
  }

}
