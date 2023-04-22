package a8.locus


import a8.shared.app.{A8LogFormatter, Logging}
import a8.shared.{CascadingHocon, ConfigMojo}
import io.undertow.Undertow
import wvlet.log.LogFormatter.SourceCodeLogFormatter
import wvlet.log.{ConsoleLogHandler, LogLevel, Logger}

object LocusMain extends Logging {

  def run(): Unit = {
    val server = new Server(resolvedModel)
    server.run()
  }

  lazy val serverConfig: Config.LocusConfig =
    ConfigMojo
      .root
      .locus
      .server
      .as[Config.LocusConfig]

  lazy val resolvedModel: ResolvedModel =
    ResolvedModel(serverConfig)

  lazy val initLogLevels = {
    Seq(
      "org.xnio",
      "org.apache.http",
      "jdk.event",
      "com.amazonaws.services.s3.internal.Mimetypes",
      "com.amazonaws.auth.AWS4Signer",
      "com.amazonaws.http.conn.ssl.SdkTLSSocketFactory",
      "javax.xml.bind",
      "com.amazonaws.internal.SdkSSLSocket",
      "com.amazonaws.requestId",
      "com.amazonaws.retry.ClockSkewAdjuster",
      "com.amazonaws.services.s3.model.transform.XmlResponsesSaxParser",
    ).foreach(l =>
      Logger(l).setLogLevel(LogLevel.INFO)
    )
  }

  def main(args: Array[String]): Unit = {
    try {
      wvlet.airframe.log.init
      Logger.rootLogger.resetHandler(new ConsoleLogHandler(A8LogFormatter.ColorConsole))
      Logger.setDefaultLogLevel(LogLevel.DEBUG)
      initLogLevels
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
