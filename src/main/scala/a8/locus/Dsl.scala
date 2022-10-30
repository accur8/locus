package a8.locus


import a8.shared.json.JsonCodec
import SharedImports._

// TODO move this code into a shared location (model3 project) that can be shared by odin/mugatu/server and this aka for https://accur8.atlassian.net/browse/ODIN-2013
object Dsl {

  object UrlPath {

    val empty: UrlPath = UrlPath(Iterable.empty, false)

    // TODO ??? harden this to properly handle the .. character (instead of dropping it) and make sure you can't escape out of the root using lots of ../../..
    def parse(path: String): UrlPath = {
      val parts =
        path
          .splitList("/")
          .foldLeft(IndexedSeq[String]()) { case (acc, p) =>
            if ( p === "." )
              acc
            else if ( p === ".." )
              acc.dropRight(1)
            else
              acc :+ p
          }
          .filterNot(p => p === "." || p === "..")
      UrlPath(parts, path.endsWith("/"))
    }

    implicit val format =
      JsonCodec.string.dimap[UrlPath](
        UrlPath.parse,
        _.toString,
      )

  }

  case class UrlPath private (
    parts: Iterable[String],
    isDirectory: Boolean,
  ) {

    def parent = UrlPath(parts.init, true)
    def last: String = parts.last

    def append(suffix: String): UrlPath =
      append(UrlPath.parse(suffix))

    def append(urlPath: UrlPath): UrlPath =
      UrlPath(parts ++ urlPath.parts, urlPath.isDirectory)

    def dropPrefix(prefixedPath: UrlPath): UrlPath =
      if ( startsWith(prefixedPath) )
        copy(parts = parts.drop(prefixedPath.parts.size))
      else
        this

    def isEmpty: Boolean = parts.isEmpty

    def startsWith(path: UrlPath): Boolean =
      path.parts.zip(parts).forall(t => t._1 === t._2)

    override def toString: String = parts.mkString("/") + (if ( isDirectory ) "/" else "")

  }

}
