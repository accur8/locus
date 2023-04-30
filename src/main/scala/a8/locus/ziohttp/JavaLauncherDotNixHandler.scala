package a8.locus.ziohttp


import a8.locus.SharedImports.*
import a8.locus.ziohttp.JavaLauncherDotNixHandler.LauncherPath
import a8.locus.ziohttp.ResolveDependencyTreeHandler.RequestHandler
import a8.shared.CompanionGen
import a8.shared.app.LoggingF
import a8.versions.RepositoryOps
import a8.versions.model.{ArtifactResponse, BranchName, ResolutionRequest}
import a8.versions.GenerateJavaLauncherDotNix
import a8.versions.GenerateJavaLauncherDotNix.BuildDescription
import a8.versions.RepositoryOps.RepoConfigPrefix
import model.*
import zio.http.{Body, Method, Request}

object JavaLauncherDotNixHandler {

  case class LauncherPath(
    path: String,
    effect: BuildDescription=>M[HttpResponse],
  )

  val buildDescriptionLauncherPath =
    LauncherPath(
      "nixBuildDescription",
      bd => HttpResponses.json(bd)
    )

  val javaLauncherInstallerDotNix =
    LauncherPath(
      "javaLauncherInstallerDotNix",
      bd => HttpResponses.Ok(bd.defaultDotNixContent)
    )

}

case class JavaLauncherDotNixHandler(
  path: LauncherPath,
)
  extends ZHttpHandler
  with LoggingF
{

  lazy val matcher: RequestMatcher =
    RequestMatcher(
      methods = Seq(Method.POST),
      paths = Seq(FullPath("api", suffix))
    )

  val suffix = path.path

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
      GenerateJavaLauncherDotNix(parms)
        .buildDescriptionT
        .flatMap { buildDescription =>
          path
            .effect(buildDescription)
        }

    }

  }

}
