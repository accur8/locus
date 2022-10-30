package a8.locus


import a8.shared.app.Logging
import a8.shared.{CascadingHocon, ConfigMojo}
import io.undertow.Undertow
import wvlet.log.{LogLevel, Logger}

object LocusMain extends Logging {

  def run(): Unit = {
    val server = new Server(resolvedModel)
    server.run()
  }

  lazy val serverConfig: Config.LocusConfig =
    ConfigMojo()
      .locus
      .server
      .as[Config.LocusConfig]

  lazy val resolvedModel: ResolvedModel =
    ResolvedModel(serverConfig)

  def main(args: Array[String]): Unit = {
    try {
      wvlet.airframe.log.init
      Logger.setDefaultLogLevel(LogLevel.DEBUG)
      run()
    } catch {
      case th: Throwable =>
        logger.error("Error running Locus", th)
        th.printStackTrace()
    }
  }

}



class Server(resolvedModel: ResolvedModel) {

  lazy val serverPort: Int = resolvedModel.config.port

  lazy val routing: Routing =
    Routing(resolvedModel)

  lazy val instance: Undertow =
    Undertow
      .builder
      .addHttpListener(serverPort, "0.0.0.0")
      .setHandler(routing.rootHandler)
      .build()


  def run(): Unit = {
    instance.start()
  }

}
