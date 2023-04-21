package a8.locus.ziohttp


import a8.locus.Config.{User, UserPrivilege}
import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel
import a8.locus.ResolvedModel.{ResolvedContent, ResolvedRepo}
import a8.locus.Routing.Router
import a8.locus.SharedImports.*
import a8.locus.UndertowAssist.HttpResponse
import a8.locus.ziohttp.ZHttpHandler.{FullPath, M}
import a8.shared.app.{Logging, LoggingF}
import a8.sync.http
import io.accur8.neodeploy.resolvedmodel.ResolvedRepository
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, StatusCodes}
import org.slf4j.MDC
import org.typelevel.ci.CIString
import a8.locus.ziohttp.ZHttpHandler.*
import zio.http.{Method, Request}

case object ListReposHandler extends ZHttpHandler with LoggingF {

  override lazy val matcher: ZHttpHandler.RequestMatcher =
    RequestMatcher(
      Seq(Method.GET),
      Seq(
        FullPath(IndexedSeq.empty),
        FullPath(IndexedSeq(CIString("index.html"))),
      ),
    )


  override def respond(req: Request): M[HttpResponse] =
    for {
      resolvedModel <- zservice[ResolvedModel]
      userService <- zservice[UserService]

    } yield {
      val repos = resolvedModel.resolvedProxyPaths
      val repoLinks =
        repos.map { repo =>
          s"<a href='/repos/${repo.name.toString}/index.html'>${repo.name.toString}</a><br/>"
        }

      htmlResponse(
        s"""<html><body>${repoLinks.mkString("\n")}</body></html>"""
      )
    }

}
