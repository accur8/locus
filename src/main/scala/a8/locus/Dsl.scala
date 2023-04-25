package a8.locus


import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}
import SharedImports.*
import a8.locus.ziohttp.model._

// TODO move this code into a shared location (model3 project) that can be shared by odin/mugatu/server and this aka for https://accur8.atlassian.net/browse/ODIN-2013
object Dsl {

  object UrlPath {

    def fromContentPath(contentPath: ContentPath): UrlPath =
      UrlPath(contentPath.parts, contentPath.isDirectory)

    def fromZHttpPath(zhttpPath: zio.http.Path): UrlPath = {
      import zio.http.Path.Segment
      val isDirectory =
        zhttpPath
          .segments
          .lastOption
          .collect {
            case Segment.Root =>
              true
          }
          .nonEmpty
      val parts =
        zhttpPath
          .segments
          .flatMap {
            case Segment.Root =>
              None
            case Segment.Text(t) =>
              Some(t)
          }
      UrlPath(parts, isDirectory)
    }

    val empty: UrlPath = UrlPath(Iterable.empty, false)

    def parse(path: String): UrlPath = {
      val pathTrim = path.trim
      val parts =
        pathTrim
          .splitList("/")
          .foldLeft(IndexedSeq[String]()) { case (acc, p) =>
            if ( p === "." )
              acc
            else if ( p === ".." )
              acc.dropRight(1)
            else
              acc :+ p
          }
          .filterNot(p => p === "." || p === "..") // extra safety check
      UrlPath(parts, pathTrim.endsWith("/"))
    }

    implicit val format: JsonTypedCodec[UrlPath, ast.JsStr] =
      JsonCodec.string.dimap[UrlPath](
        UrlPath.parse,
        _.toString,
      )

  }

  case class UrlPath(
    parts: Iterable[String],
    isDirectory: Boolean,
  ) {

    def zioPath = zio.http.Path.decode("/" + toString)

    def asContentPath: ContentPath =
      ContentPath(parts.toSeq, isDirectory)

    def contentPath(basePath: Path): Option[ContentPath] = {
      val basePathParts = parts.take(basePath.parts.size).toIndexedSeq
      ( basePathParts.map(CiString(_)) == basePath.parts )
        .toOption(
          ContentPath(parts.drop(basePath.parts.size).toIndexedSeq, isDirectory)
        )
    }

    def withIsDirectory(isDirectory: Boolean): UrlPath =
      copy(isDirectory = isDirectory)

    def dropExtension: Option[UrlPath] =
      parts.last.lastIndexOf(".") match {
        case i if i > 0 =>
          val filename = parts.last.substring(0, i)
          copy(parts = parts.dropRight(1) ++ Some(filename))
            .some
        case _ =>
          None
      }

    def appendExtension(extension: String): UrlPath =
      copy(parts = parts.dropRight(1) ++ Some(parts.last + extension))

    def parent = UrlPath(parts.init, true)
    def last: String = parts.last

    def append(suffix: String): UrlPath =
      append(UrlPath.parse(suffix))

    def append(contentPath: ContentPath): UrlPath =
      UrlPath(parts ++ contentPath.parts, contentPath.isDirectory)

    def append(urlPath: UrlPath): UrlPath =
      UrlPath(parts ++ urlPath.parts, urlPath.isDirectory)

    def dropPrefix(prefixedPath: UrlPath): UrlPath =
      if ( startsWith(prefixedPath) )
        copy(parts = parts.drop(prefixedPath.parts.size))
      else
        this

    def fullPath: String = toString

    def isEmpty: Boolean = parts.isEmpty

    def startsWith(path: UrlPath): Boolean =
      path.parts.zip(parts).forall(t => t._1 === t._2)

    override def toString: String = parts.mkString("/") + (if ( isDirectory ) "/" else "")

  }

}
