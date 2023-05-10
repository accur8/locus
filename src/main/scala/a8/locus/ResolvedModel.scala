package a8.locus


import java.io
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.util.Base64
import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.RepoContent
import a8.locus.Config.{Repo, S3Config}
import a8.locus.ResolvedModel.RepoContent.CacheFile
import a8.locus.S3Assist.BucketName
import a8.locus.UrlAssist.BasicAuth
import cats.data.Chain
import SharedImports.*
import a8.locus.model.DateTime
import a8.locus.ziohttp.ZHttpHandler
import ziohttp.model.{M, *}
import a8.shared.ZFileSystem
import a8.shared.app.{Logging, LoggingF}
import zio.stream.ZStream

import java.time.LocalDateTime
import ZFileSystem.{Directory, File}
import ZFileSystem.SymlinkHandlerDefaults.follow
import zio.http.Header.ContentType

object ResolvedModel extends LoggingF {

  case class DirectoryEntry(
    name: String,
    isDirectory: Boolean,
    resolvedRepo: ResolvedRepo,
    lastModified: Option[DateTime] = None,
    size: Option[Long] = None,
    generated: Boolean = false,
    directUrl: Option[a8.locus.model.Uri] = None,
  )

  trait ContentGenerator {
    def canGenerateFor(contentPath: ContentPath): Boolean
    def generate(context: String, contentPath: ContentPath, resolvedRepo: ResolvedRepo): M[Option[RepoContent]]
    def extraEntries(entries: Iterable[DirectoryEntry]): Iterable[DirectoryEntry]
  }

  lazy val contentGenerators: Chain[ContentGenerator] =
    Chain(GenerateIndexDotHtml, GenerateMavenMetadata, GenerateSha256)

  sealed trait RepoContent {
    val repo: ResolvedRepo
  }
  object RepoContent {
    case class TempFile(file: ZFileSystem.File, repo: ResolvedRepo) extends RepoContent
    case class CacheFile(file: ZFileSystem.File, repo: ResolvedRepo) extends RepoContent
    case class Redirect(path: UrlPath, repo: ResolvedRepo) extends RepoContent
    case class GeneratedFile(response: ZFileSystem.File, contentType: Option[ContentType], repo: ResolvedRepo) extends RepoContent
    case class GeneratedContent(repo: ResolvedRepo, contentType: Option[ContentType], content: String) extends RepoContent {
      override def toString: String = {
        val maxContentLength = 80
        val trimmedContent =
          if ( content.length > maxContentLength )
            content.substring(0, maxContentLength) + ". . ."
          else
            content
        s"GeneratedContent(${repo},${contentType},${trimmedContent})"
      }
    }

    def generateHtml(repo: ResolvedRepo, content: String): GeneratedContent =
      GeneratedContent(repo, ContentTypes.html.some, content)

  }

  enum PutResult:
    case Success, NotAllowed, AlreadyExists

}


case class ResolvedModel(
  config: Config.LocusConfig,
) extends LoggingF {

  lazy val dataRoot: Directory = ZFileSystem.dir(config.dataDirectory)

  lazy val neverCacheSet = Set(CiString("index.html"), CiString("maven-metadata.xml"))

  def isCachable(contentPath: ContentPath): Boolean = {
    val doNotCache =
      neverCacheSet.contains(CiString(contentPath.last)) || config.noCacheFilesSet.contains(CiString(contentPath.last))
    !doNotCache
  }

  lazy val resolvedProxyPaths: Iterable[ResolvedRepo] =
    config.repos.map {
      case m: Config.MultiplexerRepo =>
        ResolvedMultiplexer(m, this)
      case m: Config.UrlRepo =>
        m.url.scheme.toLowerCase match {
          case "http" | "https" =>
            ResolvedHttpRepo(m, this)
          case "s3" =>
            ResolvedS3Repo(m, this)
        }
      case l: Config.LocalRepo =>
        ResolvedLocalRepo(l, this)
    }

  def resolvedRepo(name: CiString): ResolvedRepo =
    resolvedProxyPaths.find(_.name === name).getOrError(s"unable to find proxy path ${}")

  lazy val cacheRoot: Directory = dataRoot.subdir("cache")
  lazy val tempRoot: Directory = dataRoot.subdir("temp")
  lazy val tempRootZ = ZFileSystem.dir(tempRoot.absolutePath)


  def withWorkDirectory[A](fn: a8.shared.ZFileSystem.Directory => M[A]): M[A] = {
    val directory = {
      val date = LocalDateTime.now()
      val uuid: String = java.util.UUID.randomUUID().toString.replace("-", "").take(20)
      val subPath = f"${date.getYear}/${date.getMonthValue}%02d/${date.getDayOfMonth}%02d/${uuid}"
      tempRootZ.subdir(subPath)
    }

    val wrappedEffect =
      for {
        _ <- directory.makeDirectories
        a <- fn(directory)
      } yield a

    wrappedEffect
      .ensuring(directory.deleteIfExists.logVoid)

  }

  def tempFile: M[ZFileSystem.File] = {
    zio.ZIO.acquireRelease {
      val file: File = {
        val date = LocalDateTime.now()
        val uuid: String = java.util.UUID.randomUUID().toString.replace("-", "").take(20)
        val filename = f"${date.getYear}/${date.getMonthValue}%02d/${date.getDayOfMonth}%02d/${uuid}"
        tempRootZ.file(filename)
      }
      for {
        _ <- file.parent.makeDirectories
      } yield file
    }{
      _ => zunit
//      _.deleteIfExists.logVoid
    }
  }

}
