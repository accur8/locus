package a8.locus.ziohttp


import a8.locus.SharedImports.*
import a8.locus.ziohttp.ResolveDependencyTreeHandler.RequestHandler
import a8.shared.CompanionGen
import a8.shared.app.LoggingF
import a8.versions.RepositoryOps
import a8.versions.model.{ArtifactResponse, BranchName, ResolutionRequest}
import a8.versions.GenerateJavaLauncherDotNix
import a8.versions.RepositoryOps.RepoConfigPrefix
import model.*
import zio.http.{Body, Method, Request}

object JavaLauncherDotNixHandler {

}

case class JavaLauncherDotNixHandler(launcherConfigOnly: Boolean)
  extends ZHttpHandler
  with LoggingF
{

  lazy val matcher: RequestMatcher =
    RequestMatcher(
      methods = Seq(Method.POST),
      paths = Seq(FullPath("api", suffix))
    )

  val suffix =
    if ( launcherConfigOnly )
      "javaLauncherConfigDotNix"
    else
      "javaLauncherInstallerDotNix"

  def respond(httpRequest: Request): M[HttpResponse] = {
    for {
      requestBodyStr <- httpRequest.body.asString(Utf8Charset)
      parms <- json.readF[GenerateJavaLauncherDotNix.Parms](requestBodyStr)
      response <- RequestHandler(httpRequest, parms).run
    } yield response
  }


  case class RequestHandler(
    httpRequest: Request,
    parms: GenerateJavaLauncherDotNix.Parms,
  ) {

    def run: M[HttpResponse] = {
      GenerateJavaLauncherDotNix(parms, launcherConfigOnly)
        .javaLauncherContentT
        .map(bodyStr =>
          HttpResponse(
            body = Body.fromString(bodyStr)
          )
        )

    }

  }

}
