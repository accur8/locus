package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.S3Assist.BucketName
import com.amazonaws.services.s3.AmazonS3
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry}
import a8.locus.UndertowAssist.HttpResponseBody
import SharedImports._

object GenerateIndexDotHtml extends ContentGenerator {

  override def canGenerateFor(urlPath: UrlPath): Boolean =
    urlPath.last =:= "index.html"

  override def generate(urlPath: UrlPath, resolvedRepo: ResolvedModel.ResolvedRepo): Option[HttpResponseBody] = {
    // really bad implementation of index.html
    val entriesOpt =
      resolvedRepo
        .entries(urlPath.parent)
        .map(_.toList.sortBy(_.name.toLowerCase))
    entriesOpt.map { entries =>
      HttpResponseBody.html(
s"""
<html>
  <body>
    <ul>
      <li><a href="../index.html">..</a></li>
${entries.map(e => s"<li>${link(e)} -- ${e.resolvedRepo.name}</li>").mkString("\n")}
    </ul>
  </body>
</html>
""",
      )
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
