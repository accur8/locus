package a8.locus.ziohttp


import a8.locus.SharedImports.logger
import a8.locus.ziohttp.model.{FullPath, HttpResponse, M, RequestMatcher}
import a8.shared.CompanionGen
import a8.versions.Build.BuildType
import a8.versions.RepositoryOps.RepoConfigPrefix
import a8.versions.Upgrade.LatestArtifact
import a8.versions.model.{RepoPrefix, ResolutionRequest}
import a8.versions.{RepositoryOps, ParsedVersion, VersionParser}
import coursier.Resolution
import coursier.core.{Module, Organization}
import coursier.util.Artifact
import zio.http.{Body, Headers, Method, Request}

import java.nio.charset.Charset
import a8.locus.SharedImports.*
import a8.shared.app.LoggingF
import model.*

object ResolveDependencyTreeHandler
  extends ZHttpHandler
    with LoggingF
{

  lazy val matcher: RequestMatcher =
    RequestMatcher(
      methods = Seq(Method.POST),
      paths = Seq(FullPath("api", "resolveDependencyTree"))
    )

  def respond(httpRequest: Request): M[HttpResponse] = {
    for {
      requestBodyStr <- httpRequest.body.asString(Utf8Charset)
      resolutionRequest <- json.readF[ResolutionRequest](requestBodyStr)
      response <- RequestHandler(httpRequest, resolutionRequest).run
    } yield response
  }


  case class RequestHandler(
    httpReq: Request,
    request: ResolutionRequest,
  ) {

    def run: M[HttpResponse] = zblock {

      val response = RepositoryOps.runResolve(request)

      HttpResponse(
        body = Body.fromString(response.prettyJson),
        headers = Headers(ContentTypes.json),
      )

    }

  }

}
