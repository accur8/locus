package a8.locus.model


import a8.shared.StringValue
import a8.shared.json.{JsonCodec, JsonTypedCodec}

import java.net.URI
import a8.shared.SharedImports._
import a8.shared.json.ast.JsStr

object Uri {

  implicit val jsonCodec: JsonTypedCodec[Uri, JsStr] =
    JsonCodec.string.dimap[Uri](
      Uri.parse,
      _.toString,
    )

  def parse(str: String): Uri =
    Uri(
      sttp.model.Uri
        .parse(str)
        .toOption
        .get,
    )

}

case class Uri(
  sttpUri: sttp.model.Uri,
) {

  val scheme: String = sttpUri.scheme.get

  val host: String = sttpUri.host.get

  val path =
    sttpUri
      .path
      .toNonEmpty
      .map(_.mkString("/"))

  val userInfo =
    sttpUri
      .userInfo

  def /(suffix: String): Uri = {
    copy(sttpUri.addPath(suffix.split("/").toSeq))
  }

  override def toString =
    sttpUri
      .toString()

}
