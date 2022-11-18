package a8.locus


import a8.shared.CompanionGen
import a8.versions.Build.BuildType
import a8.versions.RepositoryOps.RepoConfigPrefix
import a8.versions.Upgrade.LatestArtifact
import a8.versions.{RepositoryOps, Version, VersionParser}
import a8.versions.model.{RepoPrefix, ResolutionRequest}
import coursier.Resolution
import coursier.core.{Module, Organization}
import coursier.util.Artifact
import io.undertow.server.{HttpHandler, HttpServerExchange}
import SharedImports._
import io.undertow.util.{Headers, Methods, StatusCodes}

object ResolveDependencyTreeHandler {

}

class ResolveDependencyTreeHandler extends HttpHandler {

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    if (exchange.isInIoThread()) {
      exchange.getRequestMethod match {
        case Methods.POST =>
          exchange.dispatch(this)
        case _ =>
          exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED)
          exchange.endExchange()
      }
    } else {
      exchange.startBlocking()
      try {
        val requestBodyStr = exchange.getInputStream.readString
        val resolutionRequest = json.unsafeRead[ResolutionRequest](requestBodyStr)
        val requestHandler = RequestHandler(exchange, resolutionRequest)
        requestHandler.run()
      } catch {
        case e: Throwable =>
          logger.debug("error processing request", e)
          exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
          exchange.getResponseSender.send(e.stackTraceAsString)
      }
      exchange.endExchange()
    }
  }


  case class RequestHandler(
    exchange: HttpServerExchange,
    request: ResolutionRequest,
  ) {

    def run(): Unit = {
      val response = RepositoryOps.runResolve(request)

      exchange
        .getResponseHeaders()
        .put(Headers.CONTENT_TYPE, "application/json")

      val responseJsonStr = response.prettyJson
      exchange
        .getResponseSender
        .send(responseJsonStr)

    }

  }

}
