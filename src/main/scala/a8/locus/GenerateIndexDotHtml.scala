package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.S3Assist.BucketName
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry, RepoContent}
import SharedImports.*
import a8.locus.ResolvedModel.RepoContent.{GeneratedContent, generateHtml}
import ziohttp.model.*

object GenerateIndexDotHtml extends ContentGenerator {

  case class Column(
    name: String,
    valueFn: DirectoryEntry => Option[String],
    alignRight: Boolean = false,
  ) {
    val align = if ( alignRight ) """ align="right"""" else ""
    def cell(e: DirectoryEntry): String =
      s"<td${align}>${valueFn(e).getOrElse("")}</td>"
  }

  val Name =
    Column(
      "Name",
      e => name(e).some,
    )

  val LastModified =
    Column(
      "Last Modified",
      _.lastModified.map(_.toString),
    )

  val Size =
    Column(
      "Size",
      _.size.map(s => s"${java.text.NumberFormat.getIntegerInstance.format(s)} bytes"),
      alignRight = true,
    )

  val Repo =
    Column(
      "Repo",
      e => suffix(e).some,
    )

  val Columns =
    Vector(
      Name,
      LastModified,
      Size,
      Repo,
    )

  override def canGenerateFor(contentPath: ContentPath): Boolean =
    contentPath.last =:= "index.html" || contentPath.isDirectory

  override def generate(context: String, contentPath: ContentPath, resolvedRepo: ResolvedRepo): M[Option[RepoContent]] = {
    val dir =
      if ( contentPath.isDirectory )
        contentPath
      else
        contentPath.parent

    val breadCrumbs =
      dir
        .parts
        .inits
        .toVector
        .reverse
        .filter(_.nonEmpty)
        .map { parts =>
          s"""<a href="${context}/${parts.mkString("/")}/index.html">${parts.last}</a>"""
        }
        .mkString("&nbsp;/&nbsp;")


    // really bad implementation of index.html
    resolvedRepo
      .entries(dir)
      .map { rawEntriesOpt =>
        val rawEntries = rawEntriesOpt.toVector.flatten
        val extraEntries = resolvedRepo.contentGenerators.flatMap(_.extraEntries(rawEntries))
        val entries = (rawEntries ++ extraEntries).sortBy(e => !e.isDirectory -> e.name.toLowerCase())
        RepoContent.generateHtml(
          resolvedRepo,
              s"""
<html>
  <head>
    <style>
      table, th, td {
        border: 1px solid black;
        border-collapse: collapse;
      }
      th, td {
        padding-left: 10px;
        padding-right: 10px;
      }
    </style>
  </head>
  <body>
    <br/>
    &nbsp;&nbsp;&nbsp;<a href="/repos/">repos</a>&nbsp;/${breadCrumbs}
    <br/>
    <br/>
    <table>
      <tr>
${Columns.map(c => s"<th>${c.name}</th>").mkString("\n")}
      </tr>
${
  entries
    .map(e => Columns.map(_.cell(e)).mkString)
    .map(s => s"<tr>${s}</tr>")
    .mkString("\n")
}
    </table>
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

  def name(e: DirectoryEntry): String =
    if (e.isDirectory) {
      s"<a href='${e.name}/index.html'>${e.name}/</a>"
    } else {
      s"<a href='${e.name}'>${e.name}</a>"
    }

  override def extraEntries(entries: Iterable[DirectoryEntry]): Iterable[DirectoryEntry] =
    Iterable.empty

}
