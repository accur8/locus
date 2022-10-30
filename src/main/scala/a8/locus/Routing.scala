package a8.locus

import io.undertow.server.{HttpHandler, HttpServerExchange}

import java.util.Collections

import a8.locus.Routing.ErrorHandler
import io.undertow.Handlers
import io.undertow.security.api.{AuthenticationMechanism, AuthenticationMode}
import io.undertow.security.handlers.{AuthenticationCallHandler, AuthenticationConstraintHandler, AuthenticationMechanismsHandler, SecurityInitialHandler}
import io.undertow.security.impl.BasicAuthenticationMechanism
import io.undertow.server.handlers.encoding.{ContentEncodingRepository, DeflateEncodingProvider, EncodingHandler, GzipEncodingProvider}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.HttpString
import SharedImports._

// TODO move this code into a shared location (model3 project) that can be shared by odin/mugatu/server and this aka for https://accur8.atlassian.net/browse/ODIN-2013
object Routing {

  class ErrorHandler(next: HttpHandler) extends HttpHandler {

    override def handleRequest(exchange: HttpServerExchange): Unit = {

      try {
        next.handleRequest(exchange)
      } catch {
        case th: Throwable =>
          if ( exchange.isResponseChannelAvailable ) {
            exchange.setStatusCode(500)
            exchange.getResponseSender.send(th.stackTraceAsString)
          }
      }

    }

  }

}


case class Routing(resolvedModel: ResolvedModel) {

  def compressionHandler(next: HttpHandler) =
    new EncodingHandler(
      next,
      new ContentEncodingRepository()
        .addEncodingHandler("gzip", new GzipEncodingProvider, 100)
        .addEncodingHandler("deflate", new DeflateEncodingProvider, 10)
    )

  lazy val rootHandler =
    compressionHandler(
      new ErrorHandler(
        corsHandler(
          myHandlers
        )
      )
    )

  lazy val identityManager = UserIdentityManager(resolvedModel.config.users)

  object AccessControlHeaders {

    val maxAgeHeaderName = new HttpString("Access-Control-Max-Age")
    val maxAgeValue = (24*60*60).toString  // 24 hours aka 86400 seconds is the max according to the spec

    val allowOriginHeaderName = new HttpString("Access-Control-Allow-Origin")

    val allowHeadersHeaderName = new HttpString("Access-Control-Allow-Headers")
    val allowHeadersValue = "Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With"

  }

  val optionsRequestMethodName = new HttpString("OPTIONS")

  def corsHandler(next: HttpHandler) =
    new HttpHandler {
      override def handleRequest(exchange: HttpServerExchange): Unit = {
        import AccessControlHeaders._
        exchange.getResponseHeaders.put(maxAgeHeaderName, maxAgeValue)
        exchange.getResponseHeaders.put(allowOriginHeaderName, "*")
        exchange.getResponseHeaders.put(allowHeadersHeaderName, allowHeadersValue)
        next.handleRequest(exchange)
      }
    }

  lazy val myHandlers = {
    val pathHandler =
      resolvedModel
        .resolvedProxyPaths
        .foldLeft(Handlers.path()) { case (pathHandler, resolvedProxy) =>
          pathHandler.addPrefixPath(s"/repos/${resolvedProxy.name}/", RepoHttpHandler(resolvedModel, resolvedProxy))
        }
    pathHandler
      .addPrefixPath("/versionsVersion", new VersionsVersionHandler(resolvedModel.config))
      .addExactPath("/repos", new ListReposHandler(resolvedModel))
      .addExactPath("/repos/", new ListReposHandler(resolvedModel))
      .addExactPath("/", new RootHandler())
      .addExactPath("/index.html", new RootHandler())
  }

}
