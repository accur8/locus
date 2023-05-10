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
      .map { rawEntriesOpt =>
        val rawEntries = rawEntriesOpt.toVector.flatten
        val extraEntries = resolvedRepo.contentGenerators.flatMap(_.extraEntries(rawEntries))
        val entries = (rawEntries ++ extraEntries).sortBy(_.name.toLowerCase())
        RepoContent.generateHtml(
          resolvedRepo,
              s"""
<html>
  <body>
    <ul>
      <li><a href="../index.html">..</a></li>
${entries.map(e => s"<li>${link(e)} -- ${suffix(e)}</li>").mkString("\n")}
    </ul>
  </body>
</html>
              """.trim,
        ).some
      }
  }

  def suffix(e: DirectoryEntry): String =
    if (e.generated)
      "generated"
    else {
      e.directUrl match {
        case Some(directUrl) =>
          s"""<a href="${directUrl}">${e.resolvedRepo.name}</a> """
        case None =>
          e.resolvedRepo.name.toString
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

  override def extraEntries(entries: Iterable[DirectoryEntry]): Iterable[DirectoryEntry] =
    Iterable.empty

}
