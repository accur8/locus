package a8.locus

import a8.locus.Config.LocusConfig
import enumeratum.EnumEntry
import a8.locus.SharedImports._
import a8.shared.CompanionGen
import a8.shared.json.{EnumCodecBuilder, JsonCodec, UnionCodecBuilder}
import MxConfig._
import a8.locus.model.Uri
import zio.prelude.Equal

object Config {

  object Repo {
    implicit val format =
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
  )

  sealed trait UserPrivilege extends enumeratum.EnumEntry
  object UserPrivilege extends enumeratum.Enum[UserPrivilege] {
    val values = findValues
    case object Read extends UserPrivilege
    case object Write extends UserPrivilege
    case object Admin extends UserPrivilege

    implicit val eq = Equal.make[UserPrivilege](_ == _)

    implicit lazy val format: JsonCodec[UserPrivilege] =
      EnumCodecBuilder(this)

  }


  object User extends MxUser

  @CompanionGen
  case class User(
    name: String,
    password: String,
    privilege: UserPrivilege = UserPrivilege.Read,
  )

  object LocusConfig extends MxLocusConfig

  @CompanionGen
  case class LocusConfig(
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