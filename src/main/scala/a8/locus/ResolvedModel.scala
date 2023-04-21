package a8.locus


import java.io
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.util.Base64
import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.{ResolvedHttpRepo, ResolvedLocalRepo, ResolvedMultiplexer, ResolvedRepo, ResolvedS3Repo}
import a8.locus.Config.{Repo, S3Config}
import a8.locus.ResolvedModel.RepoContent
import a8.locus.ResolvedModel.RepoContent.CacheFile
import a8.locus.S3Assist.BucketName
import a8.locus.UndertowAssist.{ContentType, HttpResponse, HttpResponseBody}
import a8.locus.UrlAssist.BasicAuth
import cats.data.Chain
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.s3.model.{AmazonS3Exception, ObjectListing, ObjectMetadata, PutObjectRequest}
import SharedImports.*
import a8.locus.model.DateTime
import a8.locus.ziohttp.ZHttpHandler
import a8.locus.ziohttp.ZHttpHandler.M
import a8.shared.{FileSystem, ZFileSystem}
import a8.shared.FileSystem.{Directory, File}
import a8.shared.app.{Logging, LoggingF}

import java.time.LocalDateTime

object ResolvedModel {

  case class ContentPath(parts: Seq[String], isDirectory: Boolean)

  case class DirectoryEntry(
    name: String,
    isDirectory: Boolean,
    resolvedRepo: ResolvedRepo,
    lastModified: Option[DateTime] = None,
    size: Option[Long] = None,
  )

  trait ContentGenerator {
    def canGenerateFor(urlPath: UrlPath): Boolean
    def generate(urlPath: UrlPath, resolvedRepo: ResolvedRepo): Option[UndertowAssist.HttpResponseBody]
  }

  lazy val contentGenerators: Chain[ContentGenerator] =
    Chain(GenerateIndexDotHtml, GenerateMavenMetadata, GenerateSha256)

  sealed trait ResolvedRepo { self: Logging =>

    def debug(message: String): Unit =
      logger.debug(self.name.toString + " " + message)

    def model: Repo
    def controller: ResolvedModel

    def cachedContent(urlPath: UrlPath, applyCacheFilter: Boolean): Option[ResolvedContent]

    def name: CiString = model.name

    def newResolvedContent(content: Option[RepoContent], fromCache: Boolean): Option[ResolvedContent] =
      content.map(c => ResolvedContent(this, fromCache, c))

    def entries(urlPath: UrlPath): Option[Vector[DirectoryEntry]]

    def resolveContent(urlPath: UrlPath): Option[ResolvedContent]

    def put(urlPath: UrlPath, content: File): HttpStatusCode

    def resolveContentM(path: ContentPath): ZHttpHandler.M[Option[ResolvedContent]] =
      zblock(resolveContent(UrlPath.fromContentPath(path)))

    def putM(path: ContentPath, content: ZFileSystem.File): M[HttpStatusCode] =
      zblock(put(UrlPath.fromContentPath(path), FileSystem.file(content.absolutePath)))

  }

  case class ResolvedContent(
    repo: ResolvedRepo,
    fromCache: Boolean,
    content: RepoContent,
  )

  case class ResolvedMultiplexer(
    model: Config.MultiplexerRepo,
    controller: ResolvedModel,
  )
    extends ResolvedRepo
    with Logging
  {

    lazy val repoForWrites = model.repoForWrites.map(controller.resolvedRepo)
    lazy val delegates: Iterable[ResolvedRepo] = model.repos.map(controller.resolvedRepo)

    override def cachedContent(urlPath: UrlPath, applyCacheFilter: Boolean) = {
      debug(s"cachedContent using ${delegates.map(_.name).iterator.mkString(" ")}")
      delegates
        .iterator
        .map(_.cachedContent(urlPath, applyCacheFilter))
        .flatten
        .nextOption()
    }

    override def resolveContent(urlPath: UrlPath) = {
      contentGenerators
        .find(_.canGenerateFor(urlPath))
        .flatMap(_.generate(urlPath, this))
        .map(c => ResolvedContent(this, false, RepoContent.Generated(c)))
        .orElse {
          debug(s"resolveContent using ${delegates.map(_.name).iterator.mkString(" ")}")
          val cachable = controller.isCachable(urlPath)
          cachedContent(urlPath, false)
            .flatMap {
              case rc if cachable =>
                Some(rc)
              case rc =>
                rc.repo.resolveContent(urlPath)
            }
            .orElse {
              delegates
                .iterator
                .map(_.resolveContent(urlPath))
                .flatten
                .nextOption()
            }
        }
    }

    override def entries(urlPath: UrlPath): Option[Vector[DirectoryEntry]] = {
      delegates.foldLeft(none[Vector[DirectoryEntry]]) { case (acc, repo) =>
        repo.entries(urlPath) match {
          case Some(e) =>
            Some(acc.getOrElse(Vector.empty) ++ e)
          case None =>
            acc
        }
      }
    }

    override def putM(path: ContentPath, content: ZFileSystem.File): M[HttpStatusCode] =
      repoForWrites
        .map(_.putM(path, content))
        .getOrElse(zsucceed(HttpStatusCode.MethodNotAllowed))

    override def put(urlPath: UrlPath, content: File): HttpStatusCode =
      repoForWrites
        .map(_.put(urlPath, content))
        .getOrElse(HttpStatusCode.MethodNotAllowed)

  }

  case class ResolvedLocalRepo(
    model: Config.LocalRepo,
    controller: ResolvedModel,
  )
    extends ResolvedRepo
    with Logging
  {

    lazy val rootDir = {
      val d = FileSystem.dir(model.directory)
      debug(s"${name} using ${d.canonicalPath}")
      d
    }

    override def entries(urlPath: UrlPath): Option[Vector[DirectoryEntry]] = {
      val dir = rootDir.subdir(urlPath.toString)
      dir.exists().toOption(
        dir.entries().toVector.map {
          case f: File =>
            val javaIoFile = new io.File(f.canonicalPath)
            DirectoryEntry(f.name, false, this, Some(DateTime(javaIoFile.lastModified())), Some(f.size()))
          case d: Directory =>
            DirectoryEntry(d.name, true, this)
        }
      )
    }


    override def cachedContent(urlPath: UrlPath, applyCacheFilter: Boolean) = {
      val f = rootDir.file(urlPath.toString)
      newResolvedContent(f.exists().toOption(CacheFile(f)), true)
    }

    override def resolveContent(urlPath: UrlPath) =
      cachedContent(urlPath, false)


    override def put(urlPath: UrlPath, content: File): HttpStatusCode = {
      val f = rootDir.file(urlPath.toString)
      if ( !f.exists() ) {
        f.parent.makeDirectories()
        // ??? TODO harden fix this so we properly stream
        f.write(new ByteArrayInputStream(f.readBytes()))
        HttpStatusCode.Ok
      } else {
        HttpStatusCode.Conflict
      }
    }
  }

  case class ResolvedS3Repo(
    model: Config.UrlRepo,
    s3Config: S3Config,
    controller: ResolvedModel,
  )
    extends ResolvedRepo
    with Logging
    with DownloadResolver
  {

    lazy val bucket = BucketName(model.url.host)
    lazy val keyPrefix = model.url.path.map(UrlPath.parse).getOrElse(UrlPath.empty)

    override def contentUrl(urlPath: UrlPath): String = s"s3://${bucket.value}/${keyPrefix.append(urlPath)}"

    implicit lazy val amazonClient: AmazonS3 =
      AmazonS3ClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(s3Config.accessKey, s3Config.secretKey)))
        .withRegion(com.amazonaws.regions.Regions.US_EAST_1)
        .build()

    override def downloadContent(urlPath: UrlPath, tempFile: File): Option[RepoContent] = {
      import RepoContent._
      val key = keyPrefix.append(urlPath)
      try {
        val s3Object = amazonClient.getObject(bucket.value, key.toString)
        tempFile.write(s3Object.getObjectContent)
        Some(TempFile(tempFile))
      } catch {
        case e: AmazonS3Exception if e.getStatusCode === 404 =>
          if (S3Assist.isDirectory(bucket, key)) {
            GenerateIndexDotHtml
              .generate(urlPath, this)
              .map(Generated.apply)
          } else {
            None
          }
      }
    }

    override def entries(urlPath: UrlPath): Option[Vector[DirectoryEntry]] =
      S3Assist
        .listDirectory(bucket, keyPrefix.append(urlPath))
        .map { dir =>
          dir
            .entries
            .map {
              case Left(dirName) =>
                DirectoryEntry(dirName, true, this)
              case Right(s3o) =>
                val path = UrlPath.parse(s3o.getKey)
                DirectoryEntry(path.last, false, this, Some(DateTime(s3o.getLastModified)), Some(s3o.getSize))
            }
        }

    override def put(urlPath: UrlPath, content: File): HttpStatusCode = {
      val key = keyPrefix.append(urlPath).toString
      try {
        amazonClient.getObjectMetadata(bucket.value, key)
        HttpStatusCode.Conflict
      } catch {
        case s3e: AmazonS3Exception if s3e.getStatusCode === 404 =>
          val request = new PutObjectRequest(bucket.value, key, new java.io.File(content.canonicalPath))
          val result = amazonClient.putObject(request)
          HttpStatusCode.Ok
      }
    }

  }

  sealed trait RepoContent
  object RepoContent {
    case class TempFile(file: File) extends RepoContent
    case class CacheFile(file: File) extends RepoContent
    case class Redirect(path: UrlPath) extends RepoContent
    case class Generated(response: HttpResponseBody) extends RepoContent
  }


  trait DownloadResolver { self: ResolvedRepo with Logging =>

    lazy val cacheRoot = controller.cacheRoot.subdir(model.name.toString.toLowerCase)

    def cacheFile(urlPath: UrlPath) = {
      urlPath
        .parts
        .dropRight(1)
        .foldLeft(cacheRoot) { case (dir, part) =>
          dir.subdir(part)
        }
        .file(urlPath.last)
    }
    def contentUrl(urlPath: UrlPath): String

    override def cachedContent(urlPath: UrlPath, applyCacheFilter: Boolean): Option[ResolvedContent] = {
      val f = cacheFile(urlPath)
      val cachable = controller.isCachable(urlPath)
      val filterResult = !applyCacheFilter || cachable
      val exists = f.exists()
      debug(s"${if (exists) "found in cache" else "not in cache"} -- use ${filterResult} -- ${f.canonicalPath}")
      newResolvedContent(exists.toOption(CacheFile(f)), true)
        .filter(_ => filterResult)
    }

    def downloadContent(urlPath: UrlPath, tempFile: File): Option[RepoContent]

    override def resolveContent(urlPath: UrlPath): Option[ResolvedContent] = {
      import RepoContent._
      val localCacheFile = cacheFile(urlPath)
      cachedContent(urlPath, true)
        .orElse {
          controller.withWorkDirectory { workDir =>
            val tempFile = workDir.file(localCacheFile.name)
            val downloadedContent: Option[RepoContent] = downloadContent(urlPath, tempFile)
            val content =
              downloadedContent.map {
                case TempFile(file) =>
                  logger.debug(s"moving ${file.canonicalPath} to ${localCacheFile.canonicalPath}")
                  localCacheFile.parent.resolve
                  Files.move(Paths.get(file.canonicalPath), Paths.get(localCacheFile.canonicalPath), StandardCopyOption.REPLACE_EXISTING)
                  CacheFile(localCacheFile)
                case dc =>
                  dc
              }
            newResolvedContent(content, false)
          }
        }
    }
  }

  case class ResolvedHttpRepo(
    model: Config.UrlRepo,
    controller: ResolvedModel,
  )
    extends ResolvedRepo
    with Logging
    with DownloadResolver
  {

    val remoteLocationPrefix = model.url.path

    override def entries(urlPath: UrlPath): Option[Vector[DirectoryEntry]] = {
      val relativePath = urlPath.toString + "/index.html"
      val targetUrl = model.url / relativePath
      val response = UrlAssist.get(targetUrl)
      (response.status === 200).toOption {
        ReadMavenIndexDotHtml.parse(response.bodyAsStr, this)
      }
    }

    lazy val resolvedAuth: Option[BasicAuth] = {
      model.url.userInfo.map { ui =>
        ui.password match {
          case Some(password) =>
            BasicAuth(ui.username, password)
          case _ =>
            sys.error(s"invalid auth ${ui}")
        }

      }
    }


    override def contentUrl(urlPath: UrlPath): String = (model.url / urlPath.toString).toString

    override def downloadContent(urlPath: UrlPath, tempFile: File): Option[RepoContent] = {

      val relativePath = urlPath.toString

      val targetUrl = model.url / relativePath

      val response = UrlAssist.get(targetUrl, auth = resolvedAuth, followRedirects = false)

      import RepoContent._

      response.status match {
        case 200 if urlPath.isDirectory =>
          Some(Generated(HttpResponseBody.fromBytes(response.body, ContentType.html)))
        case 200 =>
          tempFile.write(response.bodyAsStream)
          Some(TempFile(tempFile))
        case 404 | 403 =>
          None
        case 302 =>
          val absoluteRemoteLocation = response.headers("Location")
          remoteLocationPrefix match {
            case Some(rlp) =>
              if ( absoluteRemoteLocation.startsWith(rlp) ) {
                val resolvedLocation = absoluteRemoteLocation.substring(rlp.length)
                Some(Redirect(UrlPath.parse(resolvedLocation)))
              } else {
                logger.warn(s"redirect doesn't match remoteLocationPrefix -- ${absoluteRemoteLocation} -- ${rlp}")
                None
              }
            case None =>
              Some(Redirect(UrlPath.parse(absoluteRemoteLocation)))
          }
        case _ =>
          logger.error(s"unsupported status of ${response.status} on GET ${targetUrl}")
          None
      }

    }

    override def putM(path: ContentPath, content: ZFileSystem.File): M[HttpStatusCode] =
      zsucceed(HttpStatusCode.MethodNotAllowed)

    override def put(urlPath: UrlPath, content: File): HttpStatusCode =
      HttpStatusCode.MethodNotAllowed

  }

}


case class ResolvedModel(
  config: Config.LocusConfig,
) extends LoggingF {

  lazy val dataRoot: Directory = FileSystem.dir(config.dataDirectory)

  lazy val neverCacheSet = Set(CiString("index.html"), CiString("maven-metadata.xml"))

  def isCachable(urlPath: UrlPath): Boolean = {
    val doNotCache =
      neverCacheSet.contains(CiString(urlPath.last)) || config.noCacheFilesSet.contains(CiString(urlPath.last))
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
            ResolvedS3Repo(m, config.s3.getOrError("s3 config is required if you have s3 url's"), this)
        }
      case l: Config.LocalRepo =>
        ResolvedLocalRepo(l, this)
    }

  def resolvedRepo(name: CiString): ResolvedRepo =
    resolvedProxyPaths.find(_.name === name).getOrError(s"unable to find proxy path ${}")

  lazy val cacheRoot: Directory = dataRoot.subdir("cache")
  lazy val tempRoot: Directory = dataRoot.subdir("temp")
  lazy val tempRootZ = ZFileSystem.dir(tempRoot.absolutePath)


  def withWorkDirectoryM[A](fn: a8.shared.ZFileSystem.Directory => ZHttpHandler.M[A]): ZHttpHandler.M[A] = {
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

  def withWorkDirectory[A](fn: Directory => A): A = {
    val name = java.util.UUID.randomUUID().toString.replaceAll("-","")
    val path = name.substring(0,2) + "/" + name.substring(2,4) + "/" + name
    val workDir = tempRoot.subdir(path)
    try {
      workDir.makeDirectories()
      fn(workDir)
    } finally {
      try {
        workDir.delete()
      } catch {
        case e: Exception =>
          logger.error(s"error cleaning up work directory ${workDir}", e)
      }
    }
  }

}
