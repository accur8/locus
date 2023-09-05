package a8.locus.ziohttp


import a8.locus.Config.{Subnet, SubnetManager, User, UserPrivilege}
import a8.locus.Dsl.UrlPath
import a8.locus.{ChecksumHandler, ResolvedModel, ResolvedRepo}
import a8.locus.ResolvedModel.PutResult
import a8.locus.ResolvedModel.RepoContent.TempFile
import a8.locus.ResolvedRepo.RepoLoggingService
import a8.shared.app.LoggingF
import org.apache.commons.net.util.SubnetUtils
import org.slf4j.MDC

import java.nio.ByteBuffer
import a8.locus.SharedImports.*
import a8.shared.ZFileSystem
import zio.http.{Body, Headers, MediaType, Method}
import model.*
import zio.http.Header.ContentType
import zio.stream.ZStream

object RepoHttpHandler {

}

case class RepoHttpHandler(resolvedModel: ResolvedModel, resolvedRepo: ResolvedRepo) extends ZHttpHandler with LoggingF {

  object ContentTypes {
    lazy val jarContentType = ContentType(MediaType("application", "java-archive"))
    lazy val textHtml = ContentType(MediaType("text", "html"))
  }

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

  val DEBUG = Method.CUSTOM("DEBUG")

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
      .resolveContent(path, true)
      .flatMap {
        case Some(_) =>
          HttpResponses.Ok()
        case None =>
          HttpResponses.NotFound()
      }

  def doGet(request: Request, path: ContentPath): M[HttpResponse] = {
    val action = request.url.queryParams.get("action").flatMap(_.headOption).map(_.toLowerCase)
    action match {
      case Some("debug") =>
        doDebug(request, path)
      case Some("clearcache") =>
        doClearCache(request, path)
      case Some(action) =>
        zsucceed(HttpResponse.notFound(s"no action named ${action} found valid actions are debug and clearcache"))
      case None =>
        doGet0(request, path)
    }
  }

  def doGet0(request: Request, path: ContentPath): M[HttpResponse] = {
    resolvedRepo
      .resolveContent(path, true)
      .flatMap {
        case Some(resolvedContent) =>
          logger.debug(s"resolved content using ${resolvedContent.repo.name} -- ${resolvedContent}")
          import a8.locus.ResolvedModel.RepoContent.*
          resolvedContent match {
            case CacheFile(file, _) =>
              responseFromFile(file, None)
            case TempFile(file, _, _) =>
              responseFromFile(file, None)
            case GeneratedContent(_, contentType, content) =>
              responseFromString(content, contentType)
            case GeneratedFile(response, contentType, _) =>
              responseFromFile(response, contentType)
            case Redirect(path, _) =>
              val rootPath = UrlPath.fromZHttpPath(request.path)
              zsucceed(HttpResponse.permanentRedirect(rootPath.append(path)))
          }
        case None =>
          zsucceed(HttpResponse.notFound(s"unable to resolve ${request.path}"))
      }
  }

  def doClearCache(request: Request, path: ContentPath): M[HttpResponse] = {
    resolvedRepo
      .clearCache(path)
      .flatMap { paths =>
        val responseBody = paths.map(p => s"${p._1.name}/${p._2}").mkString("\n")
        responseFromString(responseBody, None)
      }
  }

  def doDebug(request: Request, path: ContentPath): M[HttpResponse] = zservice[RepoLoggingService].flatMap { repoLoggingService =>
    resolvedRepo
      .resolveContent(path, true)
      .either
      .flatMap {
        case Left(th) =>
          repoLoggingService.error(s"http request failed\n${th.stackTraceAsString}")
        case Right(_) =>
          zunit
      }
      .asZIO(repoLoggingService.logs.map(_.mkString("\n")))
      .flatMap { logs =>
        responseFromString(logs, None)
      }
  }


  def responseFromString(content: String, contentType: Option[ContentType]): M[HttpResponse] =
    zsucceed(
      HttpResponse(
        body = Body.fromString(content),
        headers = Headers(contentType),
      )
    )

  def responseFromFile(file: ZFileSystem.File, contentType: Option[ContentType]): M[HttpResponse] =
    checksumHeaders(file, contentType)
      .map { headers =>
        HttpResponse(body = Body.fromFile(file.asNioPath.toFile), headers = headers)
      }

  def checksumHeaders(file: ZFileSystem.File, contentType: Option[ContentType]): M[Headers] =
    ChecksumHandler
      .responseHeaders
      .map(cs => cs.digest(file).map(dr => Headers(s"x-checksum-${cs.extension}", dr.asHexString)))
      .sequencePar
      .map { headers =>
        headers.foldLeft(Headers(contentType.orElse(defaultContentType(file))))(_ ++ _)
      }

  def defaultContentType(file: ZFileSystem.File): Option[ContentType] = {
    val path = file.path.toLowerCase
    if ( path.endsWith(".jar") ) {
      Some(ContentTypes.jarContentType)
    } else if ( path.endsWith(".pom") ) {
      Some(ContentTypes.textHtml)
    } else {
      None
    }
  }

}
