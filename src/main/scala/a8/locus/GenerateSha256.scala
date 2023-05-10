package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry, RepoContent}
import a8.locus.ResolvedModel.RepoContent.{CacheFile, GeneratedContent, TempFile}
import a8.locus.SharedImports.*
import a8.locus.model.DateTime
import a8.locus.ziohttp.model.{HttpResponse, HttpResponseBody}
import a8.shared.FileSystem
import a8.shared.ZFileSystem
import a8.shared.ZFileSystem.File
import a8.versions.{BuildTimestamp, ParsedVersion}
import org.apache.commons.codec.digest.DigestUtils
import zio.{Chunk, ZIO}

import java.time.Month
import ziohttp.model.*

object GenerateSha256 extends ContentGenerator {

  val extension = ".sha256"

  case class DigestResults(bytes: Chunk[Byte]) {
    def asHexString = org.apache.commons.codec.binary.Hex.encodeHexString(bytes.toArray)
    def asBase64String = new String(java.util.Base64.getEncoder().encode(bytes.toArray))
  }

  object impl {
    def runDigest(file: ZFileSystem.File, fn: java.io.InputStream => Array[Byte]): M[DigestResults] =
      zblock {
        val rawBytes =
          FileSystem
            .file(file.absolutePath)
            .withInputStream(fn(_))
        DigestResults(Chunk.fromArray(rawBytes))
      }
  }
  import impl._

  case object Sha256Validator extends Validator(".sha256") {
    override def digest(content: File): M[DigestResults] =
      runDigest(content, DigestUtils.sha256)
  }

  case object Md5Validator extends Validator(".md5") {
    override def digest(content: File): M[DigestResults] =
      runDigest(content, DigestUtils.md5)
  }

  case object Sha1Validator extends Validator(".sha1") {
    override def digest(content: File): M[DigestResults] =
      runDigest(content, DigestUtils.sha1)
  }

  // we do not want / need the Sha256 validator here since repos don't do Sha256
  val validators = Vector(Md5Validator, Sha1Validator)

  override def canGenerateFor(contentPath: ContentPath): Boolean =
    contentPath.parts.lastOption.exists(_.endsWith(extension))

  override def generate(sha256Path: ContentPath, resolvedRepo: ResolvedRepo): M[Option[RepoContent]] = {
    val effectOpt: Option[ZIO[Env, Throwable, Option[RepoContent]]] =
      sha256Path.dropExtension.map { basePath =>
        resolveContentAsFile(basePath, resolvedRepo).flatMap {
          case None =>
            zsucceed(None)
          case Some(contentFile) =>
            validators
              .map(_.validate(basePath, contentFile, resolvedRepo))
              .sequencePar
              .map(_.flatten)
              .flatMap { validations =>
                if (validations.forall(_ == ValidationResult.Valid)) {
                  Sha256Validator
                    .digest(contentFile)
                    .map( digest =>
                      GeneratedContent(
                        resolvedRepo,
                        contentType = None,
                        content = digest.asHexString,
                      ).some
                    )
//                    DigestUtils.sha256Hex()
//                      .withInputStream( inputStream =>
//                      Some(HttpResponseBody.fromStr(DigestUtils.sha256Hex(inputStream)))
//                    )
//                  }
                } else {
                  // respond noting failing checksums in a header
                  zsucceed(None)
                }
              }
        }
      }
    effectOpt.getOrElse(zsucceed(None))
  }


  sealed trait ValidationResult
  object ValidationResult {
    case class Invalid(message: String) extends ValidationResult
    case object Valid extends ValidationResult
  }

  abstract class Validator(extension: String) {

    def digest(content: ZFileSystem.File): M[DigestResults]

    def validate(basePath: ContentPath, contentFile: ZFileSystem.File, resolvedRepo: ResolvedRepo): M[Option[ValidationResult]] = {
      val checksumPath = basePath.appendExtension(extension)
      resolveContentAsFile(checksumPath, resolvedRepo)
        .flatMap {
          case Some(checksumFile) =>
            isChecksumValid(contentFile, checksumFile)
              .map(Some(_))
          case None =>
            zsucceed(None)
        }
    }

    def isChecksumValid(contentFile: ZFileSystem.File, checksumFile: ZFileSystem.File): M[ValidationResult] =
      for {
        expectedChecksum <- checksumFile.readAsString
        actualChecksum <- digest(contentFile)
      } yield {
        def scrub(s: String) = s.trim.toLowerCase
        if (scrub(actualChecksum.asHexString) === scrub(expectedChecksum)) {
          ValidationResult.Valid
        } else {
          ValidationResult.Invalid(s"checksum mismatch actual != expected -- ${actualChecksum} != ${expectedChecksum}")
        }
      }

  }

  def resolveContentAsFile(contentPath: ContentPath, resolvedRepo: ResolvedRepo): M[Option[File]] =
    resolvedRepo
      .resolveContent(contentPath)
      .map {
        case Some(TempFile(file, _)) =>
          Some(file)
        case Some(CacheFile(file, _)) =>
          Some(file)
        case _ =>
          None
      }

  override def extraEntries(entries: Iterable[DirectoryEntry]): Iterable[DirectoryEntry] =
    entries
      .filter(_.name.toLowerCase.endsWith(".jar"))
      .map(e => e.copy(name = e.name + ".sha256", size = Some(64), generated = true, directUrl = None))

}
