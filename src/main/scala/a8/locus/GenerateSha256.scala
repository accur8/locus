package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.ContentGenerator
import a8.locus.ResolvedModel.RepoContent.{CacheFile, TempFile}
import a8.locus.SharedImports.*
import a8.locus.model.DateTime
import a8.locus.ziohttp.model.{HttpResponse, HttpResponseBody}
import a8.shared.FileSystem.File
import a8.versions.{BuildTimestamp, Version}
import org.apache.commons.codec.digest.DigestUtils

import java.time.Month

object GenerateSha256 extends ContentGenerator {

  val extension = ".sha256"

  case object Md5Validator extends Validator(".md5") {
    override def digest(content: File): String =
      DigestUtils.md5Hex(content.readBytes())
  }

  case object Sha1Validator extends Validator(".sha1") {
    override def digest(content: File): String =
      DigestUtils.sha1Hex(content.readBytes())
  }

  val validators = Vector(Md5Validator, Sha1Validator)

  override def canGenerateFor(urlPath: UrlPath): Boolean =
    urlPath.parts.last.endsWith(extension)

  override def generate(sha256Path: UrlPath, resolvedRepo: ResolvedModel.ResolvedRepo): Option[HttpResponse] = {
    val resultOpt =
      for {
        basePath <- sha256Path.dropExtension
        contentFile <- resolveContentAsFile(basePath, resolvedRepo)
      } yield {
        val validations = validators.flatMap(_.validate(basePath, contentFile, resolvedRepo))
        if ( validations.forall(_ == ValidationResult.Valid) ) {
          Some(HttpResponseBody.fromStr(DigestUtils.sha256Hex(contentFile.readBytes())))
        } else {
          None
        }
      }
    resultOpt.flatten
  }


  sealed trait ValidationResult
  object ValidationResult {
    case class Invalid(message: String) extends ValidationResult
    case object Valid extends ValidationResult
  }

  abstract class Validator(extension: String) {

    def digest(content: File): String

    def validate(basePath: UrlPath, contentFile: File, resolvedRepo: ResolvedModel.ResolvedRepo): Option[ValidationResult] = {
      val checksumPath = basePath.appendExtension(extension)
      for {
        checksumFile <- resolveContentAsFile(checksumPath, resolvedRepo)
      } yield isChecksumValid(contentFile, checksumFile)
    }

    def isChecksumValid(contentFile: File, checksumFile: File): ValidationResult = {
      val expectedChecksum = checksumFile.readAsString().trim.toLowerCase
      val actualChecksum = digest(contentFile).toLowerCase
      if ( actualChecksum === expectedChecksum ) {
        ValidationResult.Valid
      } else {
        ValidationResult.Invalid(s"checksum mismatch actual != expected -- ${actualChecksum} != ${expectedChecksum}")
      }
    }

  }

  def resolveContentAsFile(path: UrlPath, resolvedRepo: ResolvedModel.ResolvedRepo): Option[File] =
    for {
      resolvedContent <- resolvedRepo.resolveContent(path)
      file <-
        resolvedContent.content match {
          case TempFile(file) =>
            Some(file)
          case CacheFile(file) =>
            Some(file)
          case _ =>
            None
        }
    } yield file

}
