package a8.locus

import a8.locus.Config.LocusConfig
import io.undertow.server.{HttpHandler, HttpServerExchange}

class RootHandler() extends HttpHandler {

  override def handleRequest(exchange: HttpServerExchange): Unit =
    exchange.getResponseSender.send(
s"""
<html>
  <body>
    <a href="/versionsVersion">versions version</a><br/>
    <a href="/repos/">repos</a><br/>
  </body>
</html>
"""
    )

}
