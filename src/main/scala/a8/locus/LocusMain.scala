package a8.locus

import a8.locus.Config.{LocusConfig, SubnetManager}
import a8.locus.SharedImports.*
import a8.locus.model.*
import a8.locus.ziohttp.Router
import a8.locus.{ResolvedModel, ziohttp}
import a8.shared.ConfigMojo
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import a8.shared.app.{BootstrappedIOApp, LoggingF}
import zio.http.HttpError.{BadRequest, InternalServerError}
import zio.http.Method.{GET, POST}
import zio.http.*
import zio.{Chunk, Task, ZIO}

import java.net.InetAddress

object LocusMain extends BootstrappedIOApp {

  type Env = Any

  override def runT: ZIO[BootstrapEnv, Throwable, Unit] =
    Server
      .serve(router.routes)
      .provide(Server.defaultWithPort(serverConfig.port))

  lazy val router = Router(serverConfig, resolvedModel, anonymousSubnetManager)

  lazy val serverConfig: LocusConfig =
    ConfigMojo
      .root
      .locus
      .server
      .as[LocusConfig]

  lazy val resolvedModel: ResolvedModel =
    ResolvedModel(serverConfig)

  lazy val anonymousSubnetManager: SubnetManager = {

    import org.apache.commons.net.util.SubnetUtils

    def parseSubnetUtils(subnetStr: String): Option[SubnetUtils] = {
      try {
        Some(new SubnetUtils(subnetStr))
      } catch {
        case e: Exception =>
          logger.error(s"invalid subnet in config -- ${subnetStr}", e)
          None
      }
    }

    val anonymousSubnets =
      resolvedModel
        .config
        .anonymousSubnets
        .flatMap(parseSubnetUtils)

    val proxyServerAddresses =
      resolvedModel
        .config
        .proxyServerAddresses
        .flatMap(parseSubnetUtils)

    SubnetManager(
      proxyServerAddresses,
      anonymousSubnets,
    )

  }

}
