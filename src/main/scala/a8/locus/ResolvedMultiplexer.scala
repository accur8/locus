package a8.locus


import a8.locus.ResolvedModel.{DirectoryEntry, RepoContent}
import a8.shared.app.{Logging, LoggingF}
import ziohttp.model.*
import SharedImports.*
import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.RepoContent.{CacheFile, TempFile}
import a8.locus.UrlAssist.BasicAuth
import a8.shared.ZFileSystem
import a8.shared.ZFileSystem.File
import zio.stream.ZStream

case class ResolvedMultiplexer(
  repoConfig: Config.MultiplexerRepo,
  resolvedModel: ResolvedModel,
)
  extends ResolvedRepo
  with LoggingF {

  lazy val repoForWrites = repoConfig.repoForWrites.map(resolvedModel.resolvedRepo)
  lazy val delegates: Iterable[ResolvedRepo] = repoConfig.repos.map(resolvedModel.resolvedRepo)

  def fastestRepoWithContent[A](effectFn: ResolvedRepo => M[Option[A]]): M[Option[A]] =
    delegates
      .map(effectFn)
      .sequencePar
      .map(_.flatten.headOption)

  override def cachedContent(contentPath: ContentPath): M[Option[CacheFile]] =
    if (resolvedModel.isCachable(contentPath)) {
      for {
        _ <- loggerF.debug(s"cachedContent using ${delegates.map(_.name).iterator.mkString(" ")}")
        rcOpt <- fastestRepoWithContent(_.cachedContent(contentPath))
        _ <- loggerF.debug(s"cachedContent resolved using ${rcOpt.map(_.repo.name)} ")
      } yield rcOpt
    } else {
      zsucceed(None)
    }


  override def singleDownload0(contentPath: ContentPath): M[Option[ResolvedModel.DownloadResult]] =
    fastestRepoWithContent(_.singleDownload(contentPath))

  override def entries0(contentPath: ContentPath): M[Option[Vector[DirectoryEntry]]] =
    delegates
      .map(_.entries(contentPath))
      .sequencePar
      .map { entriesPerRepo =>
        entriesPerRepo.foldLeft(none[Vector[DirectoryEntry]]) { (acc, entries) =>
          (acc, entries) match {
            case (None, None) =>
              None
            case (l, r) =>
              Some(
                l.toVector.flatten ++ r.toVector.flatten
              )
          }
        }
      }
  //    parUnorderedRepos(_.entries(contentPath))
  //      .runFold(none[Vector[DirectoryEntry]]) { case (acc, entries) =>
  //        (acc, entries) match {
  //          case (None, None) =>
  //            None
  //          case (l, r) =>
  //            Some(
  //              l.toVector.flatten ++ r.toVector.flatten
  //            )
  //        }
  //      }

  override def clearCache(contentPath: ContentPath): M[Iterable[(ResolvedRepo, String)]] = {
    for {
      superResults <- super.clearCache(contentPath)
      delegateResults <- delegates.map(_.clearCache(contentPath)).sequencePar.map(_.flatten)
    } yield superResults ++ delegateResults
  }

  override def put(path: ContentPath, content: File): M[ResolvedModel.PutResult] =
    repoForWrites
      .map(_.put(path, content))
      .getOrElse(super.put(path, content))

}

