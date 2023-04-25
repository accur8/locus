package a8.locus.ziohttp


import a8.locus.Config.{Subnet, SubnetManager, User, UserPrivilege}
import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel
import a8.locus.ResolvedModel.PutResult
import a8.locus.ResolvedModel.RepoContent.TempFile
import a8.locus.ResolvedRepo
import a8.shared.app.LoggingF
import org.apache.commons.net.util.SubnetUtils
import org.slf4j.MDC

import java.nio.ByteBuffer
import a8.locus.SharedImports.*
import zio.http.{Body, Headers, Method}
import model.*
import zio.stream.ZStream

object RepoHttpHandler {

}

case class RepoHttpHandler(resolvedModel: ResolvedModel, resolvedRepo: ResolvedRepo) extends ZHttpHandler with LoggingF {

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
        zsucceed(HttpResponse.methodNotAllowed("" + req.method + " is not allowed"))
      case Some(mh) =>
        val fullPath = UrlPath.fromZHttpPath(req.path)
        fullPath.contentPath(basePath) match {
          case Some(contentPath) =>
            for {
              userService <- zservice[UserService]
              _ <- userService.requirePrivilege(mh.requiredPrivilege, req)
              response <- mh.handler(req, contentPath)
            } yield response
          case None =>
            HttpResponse.errorz(s"mismatched paths ${fullPath} and ${basePath}.  This should not happen.")
        }
    }

  def doPut(request: Request, path: ContentPath): M[HttpResponse] =
    resolvedModel.withWorkDirectory { dir =>
      val tempFile = dir.file(path.parts.last)
      request
        .body
        .asStream
        .run(zio.stream.ZSink.fromFile(tempFile.asJioFile))
        .flatMap { _ =>
          resolvedRepo.put(path, tempFile)
        }
        .flatMap {
          case PutResult.Success =>
            HttpResponses.Ok()
          case PutResult.AlreadyExists =>
            zsucceed(HttpResponse.conflict("path already exists"))
          case PutResult.NotAllowed =>
            zsucceed(HttpResponse.methodNotAllowed("PUT method not supported on this repo"))
        }
    }

  def doHead(request: Request, path: ContentPath): M[HttpResponse] =
    resolvedRepo
      .resolveContent(path)
      .flatMap {
        case Some(_) =>
          HttpResponses.Ok()
        case None =>
          HttpResponses.NotFound()
      }

  def doGet(request: Request, path: ContentPath): M[HttpResponse] = {
    resolvedRepo
      .resolveContent(path)
      .map {
        case Some(resolvedContent) =>
          logger.debug(s"resolved content using ${resolvedContent.repo.name} -- ${resolvedContent}")
          import a8.locus.ResolvedModel.RepoContent.*
          resolvedContent match {
            case CacheFile(file, _) =>
              HttpResponse.fromFile(file)
            case TempFile(file, _) =>
              HttpResponse.fromFile(file)
            case GeneratedContent(_, contentType, content) =>
              HttpResponse(
                body = Body.fromString(content),
                headers = contentType.map(Headers(_)).getOrElse(Headers.empty),
              )
            case GeneratedFile(response, contentType, _) =>
              HttpResponse(
                body = Body.fromFile(response.asJioFile),
                headers = Headers(contentType),
              )
            case Redirect(path, _) =>
              val rootPath = UrlPath.fromZHttpPath(request.path)
              HttpResponse.permanentRedirect(rootPath.append(path))
          }
        case None =>
          HttpResponse.notFound(s"unable to resolve ${request.path}")
      }
  }

}
