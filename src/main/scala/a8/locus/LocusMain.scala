package a8.locus

import a8.locus.Config.{LocusConfig, SubnetManager}
import a8.locus.LocusMain.anonymousSubnetManager
import a8.locus.SharedImports.*
import a8.locus.model.*
import a8.locus.ziohttp.Router
import a8.locus.{ResolvedModel, ziohttp}
import a8.shared.ConfigMojo
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import a8.shared.app.{BootstrappedIOApp, LoggingF}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.{AwsClientBuilder, AwsDefaultClientBuilder}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import wvlet.log.LogLevel
import zio.http.HttpError.{BadRequest, InternalServerError}
import zio.http.Method.{GET, POST}
import zio.http.*
import zio.http.Server.RequestStreaming
import zio.{Chunk, Task, ZIO}

import java.net.InetAddress

object LocusMain extends BootstrappedIOApp {

  type Env = Any


  override def initialLogLevels: Iterable[(String, LogLevel)] =
    super.initialLogLevels ++
      Seq(
        "nodebuglogging",
        "org.xnio",
        "org.apache.http",
        "jdk.event",
        "com.amazonaws",
        "javax.xml.bind",
      ).map(_ -> LogLevel.INFO)


  override lazy val defaultAppName: String = "locus.server"

  lazy val awsCredentialsProviderZ =
    appConfig[LocusConfig]
      .map(locusConfig =>
        new AwsCredentialsProvider {
          override def resolveCredentials =
            locusConfig.s3.getOrError("s3 config is required").asAwsCredentials
        }
      )

  lazy val s3Layer =
    zio.s3.liveZIO(
      Region.US_EAST_1,
      awsCredentialsProviderZ,
    )

  def createS3Client(awsCredentialsProvider: AwsCredentialsProvider): Task[S3Client] = zblock {
    import software.amazon.awssdk.services.s3

    S3Client
      .builder()
      .region(Region.US_EAST_1)
      .credentialsProvider(awsCredentialsProvider)
      .build()

  }

  override def runT: ZIO[BootstrapEnv, Throwable, Unit] =
    (
      for {
        locusConfig <- appConfig[LocusConfig]
        awsCredentialsProvider <- awsCredentialsProviderZ
        s3Client <- createS3Client(awsCredentialsProvider)
        s3 <- zservice[zio.s3.S3]
        resolvedModel = ResolvedModel(locusConfig)
        router = Router(locusConfig, resolvedModel, anonymousSubnetManager((resolvedModel)), s3, s3Client)
        _ <- loggerF.info(s"http server is listening on port ${locusConfig.port}")
        _ <-
          Server
            .serve(router.routes)
            .provide(Server.defaultWith(_.port(locusConfig.port).keepAlive(false).withRequestStreaming(RequestStreaming.Enabled)))
      } yield ()
    ).provideSomeLayer[BootstrapEnv](s3Layer)

  def anonymousSubnetManager(resolvedModel: ResolvedModel): SubnetManager = {

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

    val proxyServerAddresses =
      resolvedModel
        .config
        .proxyServerAddresses
        .flatMap(parseSubnetUtils)

    SubnetManager(
      proxyServerAddresses,
      resolvedModel.config.anonymousSubnets.flatMap(parseSubnetUtils),
    )

  }

}
