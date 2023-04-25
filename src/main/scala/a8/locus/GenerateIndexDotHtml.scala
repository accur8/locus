package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.S3Assist.BucketName
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry, RepoContent}
import SharedImports.*
import a8.locus.ResolvedModel.RepoContent.GeneratedContent
import ziohttp.model.*

object GenerateIndexDotHtml extends ContentGenerator {

  override def canGenerateFor(contentPath: ContentPath): Boolean =
    contentPath.last =:= "index.html" || contentPath.isDirectory

  override def generate(contentPath: ContentPath, resolvedRepo: ResolvedRepo): M[Option[RepoContent]] = {
    val dir =
      if ( contentPath.isDirectory )
        contentPath
      else
        contentPath.parent
    // really bad implementation of index.html
    resolvedRepo
      .entries(dir)
      .map { entriesOpt =>
        val entries =
          entriesOpt
            .toVector
            .flatten
            .sortBy(_.name.toLowerCase)
        RepoContent.generateHtml(
          resolvedRepo,
              s"""
<html>
  <body>
    <ul>
      <li><a href="../index.html">..</a></li>
${entries.map(e => s"<li>${link(e)} -- ${e.resolvedRepo.name}</li>").mkString("\n")}
    </ul>
  </body>
</html>
              """.trim,
        ).some
       }
  }

  def link(e: DirectoryEntry): String =
    if ( e.isDirectory ) {
      s"<a href='${e.name}/index.html'>${e.name}</a>"
    } else {
      val parts = (
        e.lastModified.map(m => s" -- ${m}")
        ++ e.size.map(s => s" -- ${java.text.NumberFormat.getIntegerInstance.format(s)} bytes")
      )
      s"<a href='${e.name}'>${e.name}</a>${parts.mkString(" -- ")}"
    }

}
