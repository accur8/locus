package a8.locus

import a8.locus.Config.LocusConfig
import io.undertow.server.{HttpHandler, HttpServerExchange}

class VersionsVersionHandler(config: LocusConfig) extends HttpHandler {

  override def handleRequest(exchange: HttpServerExchange): Unit =
    exchange.getResponseSender.send(config.versionsVersion)

}
