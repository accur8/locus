package a8.locus


import a8.locus.model.Uri
import a8.shared.json.JsonCodec

import java.lang.reflect.Modifier
import java.net.{HttpURLConnection, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.language.existentials
import scala.reflect.ClassTag
import scala.xml.Elem
import SharedImports.*
import a8.locus.ziohttp.model.M
import a8.shared.ZFileSystem
import a8.shared.FileSystem

import java.io.ByteArrayInputStream
/**
  *
  *  This is a hacky hack on java.net.URL that uses reflections to set final private static fields
  *
  *  We do this since java.net.URL is widely available but at some point will replace it's usage
  *  with an HttpClient that just works.
  *
  */
object UrlAssist {

  case class Response(
    status: Int,
    statusMessage: String,
    headers: Map[String,String],
    bodyAsFile: Option[ZFileSystem.File],
  ) {

//    def bodyAsStream = new ByteArrayInputStream(body)

//    def jsonTo[A : JsonCodec]: box.Box[A] =
//      if ( status >= 200 && status < 300 ) {
//        val bodyStr = new String(body, "utf-8")
//        box.tryo(
//          JsonAssist.fromJson[A](bodyStr)
//        )
//      } else {
//        box.Failure(s"error response ${status} - ${statusMessage}")
//      }

    def asXml: zio.Task[Option[Elem]] =
      bodyAsStrOpt
        .map(_.map(xmlStr => scala.xml.XML.loadString(xmlStr)))

    def bodyAsStrOpt: zio.Task[Option[String]] =
      bodyAsFile match {
        case None =>
          zsucceed(None)
        case Some(f) =>
          f.readAsStringOpt
      }

  }

  object RequestBody {
    def apply(bytes: Array[Byte]) =
      new RequestBody {
        override def content: Array[Byte] = bytes
      }
  }

  trait RequestBody {
    def contentType: Option[String] = None
    def content: Array[Byte]
    def headers: Iterable[(String,String)] = Iterable.empty
  }

  case object EmptyBody extends RequestBody {
    override def content: Array[Byte] = new Array[Byte](0)
  }

  object FormBody {
    def apply(fields: (String,String)*) = new FormBody(fields)
  }

  case class FormBody(fields: Iterable[(String,String)], charset: String = "utf-8") extends RequestBody {
    override def contentType: Option[String] = Some(s"application/x-www-form-urlencoded; charset=${charset}")
    override def content: Array[Byte] = {
      fields
        .map(t => URLEncoder.encode(t._1, charset) + "=" + URLEncoder.encode(t._2, charset))
        .mkString("&")
        .getBytes(charset)
    }
  }

  case class BasicAuth(
    username: String,
    password: String,
  )

  def get(
    uri: Uri,
    requestHeaders: Map[String,String] = Map(),
    auth: Option[BasicAuth] = None,
    followRedirects: Boolean = false,
   ): M[Response] =
    execute(uri, "GET", requestHeaders, EmptyBody, auth, if ( followRedirects ) 10 else 0)

  private def execute(
    uri: Uri,
    requestMethod: String,
    requestHeaders: Map[String,String] = Map(),
    requestBody: RequestBody = EmptyBody,
    auth: Option[BasicAuth] = None,
    redirectsLeft: Int = 0,
  ): M[Response] =
    zservice[ResolvedModel].flatMap(_.tempFile).flatMap(tempFile =>
      zsuspend {

        val url = new java.net.URL(uri.toString)

        val conn = url.openConnection().asInstanceOf[HttpURLConnection]
        try {

          conn.setInstanceFollowRedirects(false)

          setRequestMethod(conn, requestMethod)

          auth.foreach { case BasicAuth(username, password) =>
            val encodedBytes = Base64.getEncoder.encode((username + ":" + password).getBytes)
            val authorization = "Basic " + new String(encodedBytes)
            conn.setRequestProperty("Authorization", authorization)
          }

          requestHeaders.foreach(h =>
            conn.setRequestProperty(h._1, h._2)
          )

          conn.setRequestProperty("Content-Length", requestBody.content.length.toString)

          requestBody.contentType.foreach(ct =>
            conn.setRequestProperty("Content-Type", ct)
          )

          conn.setDoInput(true)

          // If setDoOutput is set to true  on a GET, then HttpURLConnection sets the method to POST.
          if (requestBody.content.isEmpty) {
            conn.setDoOutput(false)
            conn.connect()
          } else {
            conn.setDoOutput(true)
            conn.connect()
            conn.getOutputStream.write(requestBody.content)
            conn.getOutputStream.flush()
          }

          if (conn.getResponseCode === 302 && redirectsLeft > 0) {
            Option(conn.getHeaderField("Location"))
              .map { location =>
                val redirectUri =
                  if ( location.startsWith("/") ) {
                    Uri.parse(uri.root.toString + location)
                  } else if ( location.startsWith("http") ) {
                    Uri.parse(location)
                  } else {
                    sys.error(s"invalid location ${location}")
                  }
                execute(redirectUri, requestMethod, requestHeaders, requestBody, None, redirectsLeft - 1)
              }
              .getOrElse(zfail(new RuntimeException(s"invalid redirect from ${uri}")))
          } else {
            val response =
              if (conn.getResponseCode >= 400) {
                Response(
                  conn.getResponseCode,
                  conn.getResponseMessage,
                  conn.getHeaderFields.asScala.map(t => t._1 -> t._2.get(0)).toMap,
                  None,
                )
              } else {
                FileSystem.file(tempFile.absolutePath).write(conn.getInputStream)
                Response(
                  conn.getResponseCode,
                  conn.getResponseMessage,
                  conn.getHeaderFields.asScala.map(t => t._1 -> t._2.get(0)).toMap,
                  Some(tempFile),
                )
              }
            zsucceed(response)
          }
        } finally {
          trylogo(s"swallowing error while closing url conn to ${url}")(conn.disconnect()): @scala.annotation.nowarn
        }
      }
    )

  private def setRequestMethod(httpURLConnection:HttpURLConnection, method:String): Unit = {
    try
      httpURLConnection.setRequestMethod(method)
    catch {
      case e: Exception =>
        allowMethod(method)
        httpURLConnection.setRequestMethod(method)
    }
  }


  /**
    * from here
    *    https://stackoverflow.com/questions/25163131/httpurlconnection-invalid-http-method-patch
    *
    * and for the part about setting a final static field here
    *    https://stackoverflow.com/questions/3301635/change-private-static-final-field-using-java-reflection
    *
    */
  private def allowMethod(method: String): Unit = {
    try {
      val methodsField = classOf[HttpURLConnection].getDeclaredField("methods")

      val modifiersField = classOf[java.lang.reflect.Field].getDeclaredField("modifiers")
      modifiersField.setAccessible(true)
      modifiersField.setInt(methodsField, methodsField.getModifiers & ~Modifier.FINAL)

      methodsField.setAccessible(true)
      val oldMethods = methodsField.get(null).asInstanceOf[Array[String]].toList
      val newMethods = List(method) ++ oldMethods
      methodsField.set(null /*static field*/ , newMethods.toArray)
    } catch {
      case e@(_: NoSuchFieldException | _: IllegalAccessException) =>
        throw new IllegalStateException(e)
    }
  }

}
