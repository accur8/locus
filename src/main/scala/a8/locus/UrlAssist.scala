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
import SharedImports._

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
    body: Array[Byte],
  ) {

    def bodyAsStream = new ByteArrayInputStream(body)

//    def jsonTo[A : JsonCodec]: box.Box[A] =
//      if ( status >= 200 && status < 300 ) {
//        val bodyStr = new String(body, "utf-8")
//        box.tryo(
//          JsonAssist.fromJson[A](bodyStr)
//        )
//      } else {
//        box.Failure(s"error response ${status} - ${statusMessage}")
//      }

    def asXml: Option[Elem] = {
      if (body.length == 0)
        None
      else
        Some(scala.xml.XML.loadString(bodyAsStr))
    }

    def bodyAsStr = new String(body, StandardCharsets.UTF_8)

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

  def get (
    uri: Uri,
    requestHeaders: Map[String,String] = Map(),
    auth: Option[BasicAuth] = None,
    followRedirects: Boolean = false,
   ): Response =
    execute(uri, "GET", requestHeaders, EmptyBody, auth, followRedirects)

  def execute(
    uri: Uri,
    requestMethod: String,
    requestHeaders: Map[String,String] = Map(),
    requestBody: RequestBody = EmptyBody,
    auth: Option[BasicAuth] = None,
    followRedirects: Boolean = false,
  ): Response = {

    val url = new java.net.URL(uri.toString)

    val conn = url.openConnection().asInstanceOf[HttpURLConnection]

    conn.setInstanceFollowRedirects(false)

    setRequestMethod(conn, requestMethod)

    auth.foreach { case BasicAuth(username, password) =>
      val encodedBytes = Base64.getEncoder.encode((username + ":" + password).getBytes)
      val authorization = "Basic " + new String(encodedBytes)
      conn.setRequestProperty("Authorization", authorization)
    }

    requestHeaders.foreach( h =>
      conn.setRequestProperty(h._1, h._2)
    )

    conn.setRequestProperty("Content-Length", requestBody.content.length.toString)

    requestBody.contentType.foreach ( ct =>
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

    if (conn.getResponseCode === 302 && followRedirects) {
      Option(conn.getHeaderField("Location"))
        .map { location =>
          execute(Uri.parse(location), requestMethod, requestHeaders, requestBody, None, followRedirects)
        }
        .getOrError(s"invalid redirect from ${uri}")
    } else if (conn.getResponseCode >= 400) {
      Response(
        conn.getResponseCode,
        conn.getResponseMessage,
        conn.getHeaderFields.asScala.map(t => t._1 -> t._2.get(0)).toMap,
        new Array[Byte](0),
      )
    } else {
      Response(
        conn.getResponseCode,
        conn.getResponseMessage,
        conn.getHeaderFields.asScala.map(t => t._1 -> t._2.get(0)).toMap,
        conn.getInputStream.readAllBytes(),
      )
    }
  }

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
