package a8.locus

import a8.locus.Config.LocusConfig
import enumeratum.EnumEntry
import a8.locus.SharedImports.*
import a8.shared.CompanionGen
import a8.shared.json.{EnumCodecBuilder, JsonCodec, JsonTypedCodec, UnionCodecBuilder, ast}
import MxConfig.*
import a8.locus.model.Uri
import org.apache.commons.net.util.SubnetUtils
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import zio.prelude.Equal

import java.net.{Inet4Address, Inet6Address, InetAddress, InetSocketAddress}
import scala.util.Try

object Config {

  object Repo {
    implicit val format: JsonTypedCodec[Repo, ast.JsObj] =
      UnionCodecBuilder[Repo]
        .typeFieldName("_type")
        .addType[MultiplexerRepo]("multiplexer")
        .addType[UrlRepo]("url")
        .addType[LocalRepo]("local")
        .build
  }

  sealed trait Repo {
    def name: CiString
  }

  object MultiplexerRepo extends MxMultiplexerRepo

  @CompanionGen
  case class MultiplexerRepo(
    name: CiString,
    repos: Iterable[CiString],
    repoForWrites: Option[CiString],
  ) extends Repo

  object UrlRepo extends MxUrlRepo

  @CompanionGen
  case class UrlRepo(
    name: CiString,
    url: Uri,
  ) extends Repo

  object LocalRepo extends MxLocalRepo

  @CompanionGen
  case class LocalRepo(
    name: CiString,
    directory: String,
  ) extends Repo

  object S3Config extends MxS3Config

  @CompanionGen
  case class S3Config(
    accessKey: String,
    secretKey: String,
  ) {
    def asAwsCredentials = AwsBasicCredentials.create(accessKey, secretKey)
  }

  sealed abstract class UserPrivilege(val ordinal: Int) extends enumeratum.EnumEntry
  object UserPrivilege extends enumeratum.Enum[UserPrivilege] {
    val values = findValues
    case object Read extends UserPrivilege(1)
    case object Write extends UserPrivilege(2)
    case object Admin extends UserPrivilege(3)

    implicit val eq: Equal[UserPrivilege] = Equal.make[UserPrivilege](_ == _)

    implicit lazy val format: JsonCodec[UserPrivilege] =
      EnumCodecBuilder(this)

  }


  case class Subnet(
    value: String,
  ) {
    lazy val subnetUtils: Option[SubnetUtils] = {
      try {
        Some(new SubnetUtils(value))
      } catch {
        case e: Exception =>
          logger.error(s"invalid subnet -- ${value}", e)
          None
      }
    }
  }

  case class SubnetManager(
    proxyServers: Iterable[SubnetUtils],
    anonymousSubnets: Iterable[SubnetUtils],
  ) {

    val Ipv6Localhost = List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)

    def isInSubnet(remoteAddress: InetAddress, xforwardedForHeaderOpt: Option[String]): Boolean = {

      def isInAnonymousSubnet(resolvedAddressStr: String): Boolean =
        anonymousSubnets
          .exists(_.getInfo.isInRange(resolvedAddressStr))

      val addressBytesList = remoteAddress.getAddress.toList
      val addressStr = remoteAddress.getHostAddress

      val isProxyServer =
        Try(
          proxyServers.exists(_.getInfo.isInRange(addressStr))
        ).getOrElse(false)

      val result =
        (addressBytesList, xforwardedForHeaderOpt) match {
          case (List(b0, b1, b2, b3), _) =>
            try {
              val resolvedAddress =
                xforwardedForHeaderOpt
                  .filter(_ => isProxyServer)
                  .getOrElse(addressStr)

              isInAnonymousSubnet(resolvedAddress)

            } catch {
              case e: Exception =>
                logger.error(s"unable to process access from ${addressBytesList} ${xforwardedForHeaderOpt} will report as not in anonymous subnet", e)
                false
            }
          case (Ipv6Localhost, Some(xforwardedForHeader)) =>
            isInAnonymousSubnet(xforwardedForHeader)

          case (Ipv6Localhost, None) =>
            isInAnonymousSubnet("127.0.0.1")

          case _ =>
            logger.debug(s"don't know how to handle inet address ${addressStr} -- ${addressBytesList}")
            false
        }

      logger.debug(s"allow anonymous access check from ${addressBytesList} ${xforwardedForHeaderOpt} ${isProxyServer} -- allow anonymous = ${result}")

      result

    }
  }


  object User extends MxUser {
    val anonymous = User("anonymous", "", UserPrivilege.Read)
  }

  @CompanionGen
  case class User(
    name: String,
    password: String,
    privilege: UserPrivilege = UserPrivilege.Read,
  ) {
    def hasPrivilege(privilege: UserPrivilege) =
      privilege.ordinal <= this.privilege.ordinal
  }

  object LocusConfig extends MxLocusConfig

  @CompanionGen
  case class LocusConfig(
    protocol: String = "https",
    proxyServerAddresses: Iterable[String] = Iterable("127.0.0.0/8"),
    anonymousSubnets: Iterable[String] = Iterable.empty,
    dataDirectory: String,
    s3: Option[S3Config],
    repos: Iterable[Repo],
    users: Iterable[User],
    noCacheFiles: Iterable[CiString],
    port: Int,
    versionsVersion: String,
    realm: String = "Accur8 Repo",
  ) {

    lazy val noCacheFilesSet: Set[CiString] = noCacheFiles.toSet

  }

}
