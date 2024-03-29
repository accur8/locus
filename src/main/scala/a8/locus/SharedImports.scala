package a8.locus

import a8.locus.ResolvedRepo.RepoLoggingService
import a8.locus.ziohttp.model.M
import a8.shared.app.Logging
import a8.shared.json.ast.JsStr
import a8.shared.json.{JsonCodec, JsonTypedCodec}
import zio.prelude.Equal

import scala.language.implicitConversions

object SharedImports extends a8.shared.SharedImports with Logging {

  type CiString = CIString
  val CiString = CIString
  implicit val ciStringCodec: JsonTypedCodec[CiString, JsStr] =
    JsonTypedCodec.string.dimap[CiString](
      CIString(_),
      _.toString,
    )
  implicit val ciStringEq: Equal[CiString] = Equal.make[CiString](_ == _)


  implicit class MoreStringOps(val s: String) extends AnyVal {
    def =:=(right: String) = s.equalsIgnoreCase(right)
  }

  def pipe(input: java.io.InputStream, output: java.io.OutputStream): Unit = {
    try {
      val buffer = new Array[Byte](1024 * 8)
      var ct = 0
      while (ct >= 0) {
        ct = input.read(buffer)
        if (ct > 0) {
          output.write(buffer, 0, ct)
        }
      }
    } finally {
      try {
        input.close()
      } catch {
        case th: Throwable =>
          logger.debug("swalling error on close", th)
      }
      try {
        output.close()
      } catch {
        case th: Throwable =>
          logger.debug("swalling error on close", th)
      }
    }

  }

  implicit def moreImplicitZioOps[A](effect: M[A]): MoreZioOps[A] =
    new MoreZioOps(effect)

  class MoreZioOps[A](effect: M[A])(implicit trace: zio.Trace) {

    /**
      * wraps the effect to log the start of the effect and
      * it's success value or its error value
      */
    def traceDebug(context: String, maxLength: Int = 256)(implicit loggerF: a8.shared.app.LoggerF, trace: zio.Trace): M[A] =
      zservice[RepoLoggingService].flatMap( repoLoggingService =>
        repoLoggingService.debug(s"start ${context}")
          .flatMap(_ => effect)
          .flatMap { v =>
            val vToString =
              v.toString match {
                case s if s.length > maxLength =>
                  s.substring(0, maxLength) + ". . ."
                case s =>
                  s
              }
            repoLoggingService.debug(s"success ${context} -- ${vToString}")
              .as(v)
          }
          .either
          .flatMap {
            case Right(v) =>
              zsucceed(v)
            case Left(th) =>
              repoLoggingService.debug(s"error ${context}\n${th.stackTraceAsString}")
                .asZIO(zfail(th))
          }
      )
  }
}
