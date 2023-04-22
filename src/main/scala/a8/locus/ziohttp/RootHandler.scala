package a8.locus.ziohttp


import a8.locus.Config.LocusConfig
import a8.sync.http
import org.typelevel.ci.CIString
import zio.http.{Method, Response}
import a8.locus.ziohttp.model.*
import a8.locus.SharedImports.*
import model._

case object RootHandler extends ZHttpHandler {

  override lazy val matcher: RequestMatcher =
    RequestMatcher(
      Seq(Method.GET),
      Seq(
        FullPath(IndexedSeq.empty),
        FullPath(IndexedSeq(CIString("index.html"))),
      ),
    )


  override def respond(req: Request): M[HttpResponse] =
    htmlResponseZ(
      s"""
<html>
  <body>
    <a href="/versionsVersion">versions version</a><br/>
    <a href="/repos/">repos</a><br/>
  </body>
</html>
""".trim
    )

}
