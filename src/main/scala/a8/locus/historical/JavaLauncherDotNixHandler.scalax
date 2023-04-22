package a8.locus


import a8.locus.SharedImports._
import a8.shared.CompanionGen
import a8.versions.RepositoryOps
import a8.versions.model.{ArtifactResponse, BranchName, ResolutionRequest}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Methods, StatusCodes}
import a8.versions.GenerateJavaLauncherDotNix
import a8.versions.RepositoryOps.RepoConfigPrefix

object JavaLauncherDotNixHandler {

}

case class JavaLauncherDotNixHandler(launcherConfigOnly: Boolean) extends HttpHandler {

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
        val parms = json.unsafeRead[GenerateJavaLauncherDotNix.Parms](requestBodyStr)
        val requestHandler = RequestHandler(exchange, parms)
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
    parms: GenerateJavaLauncherDotNix.Parms,
  ) {

    def run(): Unit = {

      val content =
        zio.Unsafe.unsafe { implicit unsafe =>
          val contentEffect = GenerateJavaLauncherDotNix(parms, launcherConfigOnly)
          zio.Runtime.default.unsafe.run(
            contentEffect.javaLauncherContentT
          ).getOrThrowFiberFailure()
        }

      exchange
        .getResponseSender
        .send(content)

    }

  }

}
