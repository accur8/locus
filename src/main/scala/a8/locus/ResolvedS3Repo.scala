package a8.locus


import a8.locus.ResolvedModel.{DirectoryEntry, PutResult, RepoContent}
import a8.shared.app.{Logging, LoggingF}
import ziohttp.model.*
import SharedImports.*
import a8.locus.Config.S3Config
import a8.locus.Dsl.UrlPath
import a8.locus.UrlAssist.BasicAuth
import a8.shared.ZFileSystem
import a8.shared.ZFileSystem.File
import zio.stream.{ZSink, ZStream}
import a8.locus.S3Assist.BucketName
import a8.locus.model.DateTime
import zio.http.Header.Authorization.Digest

case class ResolvedS3Repo(
  repoConfig: Config.UrlRepo,
  resolvedModel: ResolvedModel,
)
  extends ResolvedRepo
  with LoggingF
{

  lazy val bucket = BucketName(repoConfig.url.host)
  lazy val keyPrefix =
    repoConfig
      .url
      .path
      .map(UrlPath.parse)
      .getOrElse(UrlPath.empty)
      .asContentPath

  override def downloadContent0(contentPath: ContentPath): M[Option[RepoContent]] =  {
    import RepoContent._
    val key = keyPrefix.append(contentPath).asFile.fullPath
    val content = zio.s3.getObject(bucket.value, key)
    val effect =
      for {
        tempFile <- resolvedModel.tempFile
        _ <- tempFile.parent.resolve
        _ <- content.run(ZSink.fromFile(tempFile.asJioFile))(zio.Trace("nodebuglogging","",0))
      } yield TempFile(tempFile, this)
    S3Assist.handleNotFound(effect)
  }


  override def entries0(contentPath: ContentPath): M[Option[Vector[DirectoryEntry]]] = {
    val baseUrl = repoConfig.url / contentPath
    val key = keyPrefix.append(contentPath).asFile
    S3Assist
      .list2(bucket, key)
      .map {
        _.map {
          case Left(dirName) =>
            DirectoryEntry(dirName, true, this)
          case Right(s3o) =>
            val path = UrlPath.parse(s3o.key)
            val url = baseUrl / path.last
            DirectoryEntry(path.last, false, this, Some(DateTime(s3o.lastModified.getEpochSecond * 1000)), Some(s3o.size), directUrl = Some(url))
        }
      }
      .either
      .flatMap {
        case Right(v) =>
          zsucceed(Some(v))
        case Left(e: software.amazon.awssdk.services.s3.model.S3Exception) if e.statusCode() == 404 =>
          zsucceed(None)
        case Left(e) =>
          zfail(e)
      }
//      .onError( th =>
//        loggerF.warn(s"unexpected error getting entries for ${contentPath}", th)
//      )
  }

  def calculateMd5(file: File): M[ChecksumHandler.DigestResults] =
    ChecksumHandler.Md5.digest(file)

  override def put(contentPath: ContentPath, contentFile: File): M[PutResult] = {
    val key = keyPrefix.append(contentPath).toString
    S3Assist
      .handleNotFound {
        zio.s3.getObjectMetadata(bucket.value, key)
      }
      .flatMap {
        case None =>
          for {
            size <- contentFile.size
            md5 <- calculateMd5(contentFile)
            _ <- zio.s3.putObject(bucket.value, key, size, ZStream.fromFile(contentFile.asJioFile), contentMD5 = Some(md5.asBase64String))
          } yield PutResult.Success
        case Some(_) =>
          zsucceed(PutResult.AlreadyExists)
      }
  }

}
