package a8.locus


import a8.shared.ZFileSystem
import a8.shared.ZFileSystem.{Directory, File}
import a8.shared.app.LoggingF
import ZFileSystem.SymlinkHandlerDefaults.follow
import a8.locus.ziohttp.model.*
import SharedImports.*
import a8.locus.ResolvedModel.*
import a8.locus.ResolvedModel.RepoContent.CacheFile
import a8.locus.model.DateTime

case class ResolvedLocalRepo(
  repoConfig: Config.LocalRepo,
  resolvedModel: ResolvedModel,
)
  extends ResolvedRepo
  with LoggingF
{

  lazy val rootDir = {
    val d = ZFileSystem.dir(repoConfig.directory)
    debug(s"${name} using ${d.canonicalPath}")
    d
  }

  override def cachedContent(contentPath: ContentPath): M[Option[CacheFile]] = {
    val file = rootDir.file(contentPath.fullPath)
    file.existsAsFile.map(
      _.toOption(
        CacheFile(file, this)
      )
    )
  }

  override def downloadContent0(contentPath: ContentPath): M[Option[RepoContent]] =
    zsucceed(None)

  override def entries0(contentPath: ContentPath): M[Option[Vector[DirectoryEntry]]] = {
    val dir = rootDir.subdir(contentPath.toString)
    for {
      exists <- dir.exists
      entries <- dir.entries
      results <-
        zblock(
          exists
            .toOption(None)
            .getOrElse(
              Some(entries.toVector.map {
                case f: File =>
                  val javaIoFile = f.asJioFile
                  DirectoryEntry(f.name, false, this, Some(DateTime(javaIoFile.lastModified())), Some(javaIoFile.length()), false)
                case d: Directory =>
                  DirectoryEntry(d.name, true, this, generated = false)
              })
            )
        )
    } yield results

  }



  override def put(contentPath: ContentPath, content: File): M[PutResult] = {
    val f = rootDir.file(contentPath.toString)
    f.exists.flatMap { exists =>
      if (!exists) {
        for {
          _ <- f.parent.makeDirectories
          _ <- content.copyTo(f)
        } yield PutResult.Success
      } else {
        zsucceed(PutResult.AlreadyExists)
      }
    }
  }
}
