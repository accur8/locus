package a8.locus


import a8.shared.app.{LoggerF, LoggingF}
import a8.locus.ResolvedModel.*
import ziohttp.model.*
import a8.locus.Config.*
import SharedImports.*
import a8.locus.ResolvedModel.RepoContent.{CacheFile, TempFile}
import a8.shared.ZFileSystem
import ZFileSystem.SymlinkHandlerDefaults.follow
import a8.locus.ChecksumHandler.{Checksum, ValidationResult}
import a8.locus.ResolvedRepo.RepoLoggingService
import a8.shared.ZFileSystem.File
import wvlet.log.LogLevel
import zio.{Task, Trace, UIO}

import scala.collection.mutable

object ResolvedRepo {

  val standardRetryCount = 1

  lazy val defaultContentGenerators: Vector[ContentGenerator] =
    Vector(ChecksumGenerator, GenerateMavenMetadata, GenerateIndexDotHtml)

  object RepoLoggingService {
    def create: RepoLoggingService =
      new RepoLoggingService:
        val started = System.currentTimeMillis()
        var logs_ = List.empty[String]
        def log(level: LogLevel, message: String, throwable: Throwable)(implicit loggerF: LoggerF, trace: Trace): Task[Unit] = {
          zblock {
            this.synchronized {
              val delta = System.currentTimeMillis() - started
              logs_ ::= f"${delta}%4d - ${level.name}%5s - ${message}${Option(throwable).map(" - " + _.getMessage).mkString}"
            }
          }.asZIO(loggerF.log(level, message, Option(throwable)))
        }

        override def logs: UIO[Iterable[String]] =
          zsucceed(logs_.reverse)
  }
  trait RepoLoggingService {

    def log(level: LogLevel, message: String, throwable: Throwable)(implicit loggerF: LoggerF, trace: Trace): Task[Unit]

    def trace(message: String, throwable: Throwable = null)(implicit loggerF: LoggerF, trace: Trace): zio.Task[Unit] =
      log(LogLevel.TRACE, message, throwable)

    def debug(message: String, throwable: Throwable = null)(implicit loggerF: LoggerF, trace: Trace): zio.Task[Unit] =
      log(LogLevel.DEBUG, message, throwable)

    def info(message: String, throwable: Throwable = null)(implicit loggerF: LoggerF, trace: Trace): zio.Task[Unit] =
      log(LogLevel.INFO, message, throwable)

    def warn(message: String, throwable: Throwable = null)(implicit loggerF: LoggerF, trace: Trace): zio.Task[Unit] =
      log(LogLevel.WARN, message, throwable)

    def error(message: String, throwable: Throwable = null)(implicit loggerF: LoggerF, trace: Trace): zio.Task[Unit] =
      log(LogLevel.ERROR, message, throwable)

    def logs: zio.UIO[Iterable[String]]

  }

}

trait ResolvedRepo { self: LoggingF =>

  lazy val cacheRoot = resolvedModel.cacheRoot.subdir(repoConfig.name.toString.toLowerCase)

  lazy val generatedChecksumHandlers: Vector[ChecksumHandler] =
    repoConfig
      .generatedChecksums
      .flatMap { checksumName =>
        val checksumNameLc = checksumName.toLowerCase
        if ( checksumNameLc == "all" ) {
          ChecksumHandler.all
        } else {
          ChecksumHandler.all.find(_.extensionLc == checksumNameLc) match {
            case Some(cs) =>
              cs.some
            case None =>
              logger.warn(s"unable to find checksum ${checksumName} in ${repoConfig.name}")
              None
          }
        }
      }
      .distinct

  def debug(message: String): Unit =
    logger.debug(self.name.toString + " " + message)

  val repoConfig: Repo
  val resolvedModel: ResolvedModel

  lazy val name: CiString = repoConfig.name

  final def entries(contentPath: ContentPath): M[Option[Vector[DirectoryEntry]]] =
    entries0(contentPath)
      .traceDebug(s"${name}.entries(${contentPath})")

  def entries0(contentPath: ContentPath): M[Option[Vector[DirectoryEntry]]]

  def downloadContent(contentPath: ContentPath): M[Option[RepoContent]] =
    downloadContentWithRetries(contentPath, ResolvedRepo.standardRetryCount)
      .traceDebug(s"${name}.downloadContent(${contentPath})")

  def downloadContentWithRetries(contentPath: ContentPath, retriesLeft: Int): M[Option[RepoContent]] = zservice[RepoLoggingService].flatMap { repoLoggingService =>
    def retry =
      if ( retriesLeft >= 1 ) {
        downloadContentWithRetries(contentPath, retriesLeft - 1)
      } else {
        repoLoggingService.error(s"repo ${name} unable to download ${contentPath} no retries left see previous error messages")
          .asZIO(zsucceed(None))
      }
    singleDownload(contentPath)
      .either
      .flatMap {
        case Left(th) =>
          repoLoggingService.warn(s"repo ${name} error downloading ${contentPath} ${retriesLeft} retries left", th)
            .asZIO(retry)
        case Right(None) =>
          zsucceed(None)
        case Right(Some(DownloadResult.Success(repo, file))) =>
          repo
            .validateChecksums(file, contentPath)
            .either
            .flatMap {
              case Right(checksums) =>
                repoLoggingService.trace(s"repo ${name} - ${contentPath} - ${if ( checksums.isEmpty ) "pass because no checksums" else s"checksums ${checksums.map(_.extension).mkString(" ")} pass"}")
                  .as(Some(TempFile(file, repo, checksums)))
              case Left(th) =>
                repoLoggingService.warn(s"repo ${name} error downloading ${contentPath} ${retriesLeft} retries left -- failed checksums", th)
                  .asZIO(retry)
            }
        case Right(Some(DownloadResult.AsRepoContent(repoContent))) =>
          zsucceed(Some(repoContent))

      }
  }

  final def singleDownload(contentPath: ContentPath): M[Option[DownloadResult]] =
    singleDownload0(contentPath)
      .traceDebug(s"${name}.singleDownload(${contentPath})")

  def singleDownload0(contentPath: ContentPath): M[Option[DownloadResult]]

  def put(path: ContentPath, content: ZFileSystem.File): M[PutResult] =
    zsucceed(PutResult.NotAllowed)

  lazy val contentGenerators: Vector[ContentGenerator] = ResolvedRepo.defaultContentGenerators

  final def resolveGeneratedContent(path: ContentPath): M[Option[RepoContent]] =
    contentGenerators
      .find(_.canGenerateFor(path))
      .map(_.generate(s"/repos/${name.toString}", path, this))
      .getOrElse(zsucceed(None))

  def resolveContent(path: ContentPath, includeGeneratedContent: Boolean): M[Option[RepoContent]] = zsuspend {

    val effects: Seq[M[Option[RepoContent]]] = {
      includeGeneratedContent.toOption(resolveGeneratedContent(path)).toList ::: List(
        cachedContent(path),
        downloadContent(path),
      )
    }

    val start: zio.ZIO[Env, Throwable, Option[RepoContent]] =
      zsucceed(none[RepoContent])

    effects
      .foldLeft(start) { (accEffect, nextEffect) =>
        accEffect
          .flatMap {
            case None =>
              nextEffect
            case o =>
              zsucceed(o)
          }
      }
      .tap(rco =>
        writeToCache(path, rco)
          .logVoid
      )
      .traceDebug(s"${name}.resolveContent(${path})")

  }

  def writeToCache(path: ContentPath, repoContent: Option[RepoContent]): M[Unit] =
    repoContent match {
      case _ if ChecksumHandler.isChecksum(path) =>
        zunit
      case Some(TempFile(tempFile, repo, checksums)) if resolvedModel.isCachable(path) =>
        val cacheFile = repo.cacheFile(path)
        for {
          repoLoggingService <- zservice[RepoLoggingService]
          _ <- cacheFile.parent.makeDirectories
          _ <- tempFile.copyTo(cacheFile)
          _ <- repoLoggingService.trace(s"wrote ${cacheFile.absolutePath}")
          _ <-
            checksums
              .map { checksum =>
                val checksumCacheFile = repo.cacheFile(path.appendExtension(checksum.extension))
                checksumCacheFile.write(checksum.value)
                  .asZIO(repoLoggingService.trace(s"wrote ${checksumCacheFile.absolutePath}"))
              }
              .sequencePar
        } yield()
      case _ =>
        zunit
    }

  def cacheFile(contentPath: ContentPath): File = {
    contentPath
      .parts
      .dropRight(1)
      .foldLeft(cacheRoot) { case (dir, part) =>
        dir.subdir(part)
      }
      .file(contentPath.last)
  }

  def validateChecksums(contentFile: File, contentPath: ContentPath): M[Seq[Checksum]] = {
    if ( ChecksumHandler.isChecksum(contentPath) ) {
      zsucceed(Seq.empty)
    } else {
      ChecksumHandler
        .validators
        .map(_.validate(contentPath, contentFile, this))
        .sequencePar
        .map(_.flatten)
        .flatMap { results =>
          val invalids = results.collect { case e: ValidationResult.Invalid => e }
          val valids = results.collect { case e: ValidationResult.Valid => e }
          if ( invalids.isEmpty ) {
            zsucceed(valids.map(_.checksum))
          } else {
            val msg = s"repo ${name} checksum failed for ${contentPath} temp file ${contentFile.absolutePath} -- ${invalids.mkString("  --  ")}"
            zfail(new RuntimeException(msg))
          }
        }
    }
  }


  def cachedContent(contentPath: ContentPath): M[Option[CacheFile]] = {
    if ( resolvedModel.isCachable(contentPath) ) {
      val cacheFile0 = cacheFile(contentPath)
      cacheFile0
        .existsAsFile
        .map(_.toOption(CacheFile(cacheFile0, this)))
        .traceDebug(s"${name}.cachedContent(${contentPath}) - ${cacheFile0.absolutePath}")
    } else {
      zsucceed(None)
    }
  }

  /**
    * works on directories and partial matches
    * @param contentPath
    * @return
    */
  def clearCache(contentPath: ContentPath): M[Iterable[(ResolvedRepo,String)]] = {
    val path = cacheFile(contentPath)
    val isDirectory = java.nio.file.Files.isDirectory(path.asNioPath)
    val (directory, filter) =
      if ( isDirectory ) {
        ZFileSystem.dir(path.absolutePath) -> { (p: ZFileSystem.Path) => true }
      } else {
        val prefix = contentPath.last
        path.parent -> { (p: ZFileSystem.Path) => p.name.startsWith(prefix) }
      }
    directory
      .entries
      .map(_.filter(filter))
      .flatMap { entries =>
        val response = entries.map(e => this -> e.absolutePath)
        entries
          .map(_.delete)
          .sequencePar
          .as(response)
      }
  }

  override def toString: String = s"Repo(${name})"

}
