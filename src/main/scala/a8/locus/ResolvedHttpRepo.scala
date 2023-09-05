package a8.locus


import a8.locus.ResolvedModel.{DirectoryEntry, RepoContent}
import a8.shared.app.{Logging, LoggingF}
import ziohttp.model.*
import SharedImports.*
import a8.locus.Dsl.UrlPath
import a8.locus.UrlAssist.BasicAuth
import a8.shared.ZFileSystem
import a8.shared.ZFileSystem.File
import zio.http.Body
import zio.stream.ZSink

case class ResolvedHttpRepo(
  repoConfig: Config.UrlRepo,
  resolvedModel: ResolvedModel,
)
  extends ResolvedRepo
    with LoggingF
{

  val remoteLocationPrefix = repoConfig.url.path

  override def entries0(contentPath: ContentPath): M[Option[Vector[DirectoryEntry]]] = {
    val relativePath = contentPath.fullPath
    val targetUrl = repoConfig.url / relativePath
    UrlAssist.get(targetUrl, followRedirects = true).flatMap { response =>
      (response.status === 200).toOption(
        response
          .bodyAsStrOpt
          .map(_.map(b => ReadMavenIndexDotHtml.parse(targetUrl, b, this)))
      ).getOrElse(zsucceed(None))
    }
  }

  lazy val resolvedAuth: Option[BasicAuth] = {
    repoConfig.url.userInfo.map { ui =>
      ui.password match {
        case Some(password) =>
          BasicAuth(ui.username, password)
        case _ =>
          sys.error(s"invalid auth ${ui}")
      }

    }
  }

//  override def contentUrl(contentPath: ContentPath): String = (model.url / contentPath).toString

  override def singleDownload(contentPath: ContentPath): M[Option[ResolvedModel.DownloadResult]] = {

    import ResolvedModel.DownloadResult._

    val targetUrl = repoConfig.url / contentPath

    UrlAssist.get(targetUrl, auth = resolvedAuth, followRedirects = false).flatMap { response =>

      import RepoContent._

      response.status match {
        case 200 if contentPath.isDirectory =>
          zsucceed(
            Some(AsRepoContent(GeneratedFile(response.bodyAsFile.get, ContentTypes.html.some, this)))
          )
        case 200 =>
          response.bodyAsFile match {
            case None =>
              zfail(new RuntimeException("got no response body on a 200 this should not happen"))
            case Some(f) =>
              zsucceed(Some(Success(f)))
          }
        case 404 | 403 =>
          zsucceed(None)
        case 302 =>
          val absoluteRemoteLocation = response.headers("Location")
          remoteLocationPrefix match {
            case Some(rlp) =>
              if (absoluteRemoteLocation.startsWith(rlp)) {
                val resolvedLocation = absoluteRemoteLocation.substring(rlp.length)
                zsucceed(Some(AsRepoContent(Redirect(UrlPath.parse(resolvedLocation), this))))
              } else {
                logger.warn(s"redirect doesn't match remoteLocationPrefix -- ${absoluteRemoteLocation} -- ${rlp}")
                zsucceed(None)
              }
            case None =>
              zsucceed(Some(AsRepoContent(Redirect(UrlPath.parse(absoluteRemoteLocation), this))))
          }
        case _ =>
          logger.error(s"unsupported status of ${response.status} on GET ${targetUrl}")
          zsucceed(None)
      }
    }

  }

}
