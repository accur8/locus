package a8.locus


import a8.shared.app.LoggingF
import a8.locus.ResolvedModel.*
import ziohttp.model.*
import a8.locus.Config.*
import SharedImports.*
import a8.locus.ResolvedModel.RepoContent.{CacheFile, TempFile}
import a8.shared.ZFileSystem
import ZFileSystem.SymlinkHandlerDefaults.follow

object ResolvedRepo {

  lazy val defaultContentGenerators: Vector[ContentGenerator] =
    Vector(GenerateSha256, GenerateMavenMetadata, GenerateIndexDotHtml)

}

trait ResolvedRepo { self: LoggingF =>

  lazy val cacheRoot = resolvedModel.cacheRoot.subdir(repoConfig.name.toString.toLowerCase)

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
    downloadContent0(contentPath)
      .traceDebug(s"${name}.downloadContent(${contentPath})")

  def downloadContent0(contentPath: ContentPath): M[Option[RepoContent]]

  def put(path: ContentPath, content: ZFileSystem.File): M[PutResult] =
    zsucceed(PutResult.NotAllowed)

  lazy val contentGenerators: Vector[ContentGenerator] = ResolvedRepo.defaultContentGenerators

  final def resolveGeneratedContent(path: ContentPath): M[Option[RepoContent]] =
    contentGenerators
      .find(_.canGenerateFor(path))
      .map(_.generate(s"/repos/${name.toString}",path, this))
      .getOrElse(zsucceed(None))

  def resolveContent(path: ContentPath): M[Option[RepoContent]] = {

    val effects: Seq[M[Option[RepoContent]]] =
      List(
        resolveGeneratedContent(path),
        cachedContent(path),
        downloadContent(path),
      )

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
      case Some(TempFile(tempFile, repo)) if resolvedModel.isCachable(path) =>
        val cf = repo.cacheFile(path)
        cf.parent.makeDirectories
          .flatMap(_ =>
            tempFile.copyTo(cf)
          )
      case _ =>
        zunit
    }

  def cacheFile(contentPath: ContentPath) = {
    contentPath
      .parts
      .dropRight(1)
      .foldLeft(cacheRoot) { case (dir, part) =>
        dir.subdir(part)
      }
      .file(contentPath.last)
  }

  def cachedContent(contentPath: ContentPath): M[Option[CacheFile]] = {
    if ( resolvedModel.isCachable(contentPath) ) {
      val cacheFile0 = cacheFile(contentPath)
      cacheFile0
        .existsAsFile
        .map(_.toOption(CacheFile(cacheFile0, this)))
        .traceDebug(s"${name}.cachedContent(${contentPath})")
    } else {
      zsucceed(None)
    }
  }

  override def toString: String = s"Repo(${name})"

}
