package a8.locus


import a8.locus.ChecksumHandler.{Checksum, ValidationResult}
import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry, RepoContent, contentGenerators}
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

object ChecksumHandler {

  case class Checksum(extension: String, value: String)

  def isChecksum(contentPath: ContentPath): Boolean = {
    val e = contentPath.extension.toLowerCase
    val result = all.exists(_.extension == e)
    result
  }

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

  case object Sha256 extends ChecksumHandler("sha256", false) {
    override def digest(content: File): M[DigestResults] =
      runDigest(content, DigestUtils.sha256)
  }

  case object Md5 extends ChecksumHandler("md5", true) {
    override def digest(content: File): M[DigestResults] =
      runDigest(content, DigestUtils.md5)
  }

  case object Sha1 extends ChecksumHandler("sha1", true) {
    override def digest(content: File): M[DigestResults] =
      runDigest(content, DigestUtils.sha1)
  }

  // we do not want / need the Sha256 validator here since repos don't do Sha256
  lazy val validators = Vector(Md5, Sha1)
  lazy val responseHeaders = Vector(Md5, Sha1)
  lazy val all = validators ++ Vector(Sha256)

  sealed trait ValidationResult
  object ValidationResult {
    case class Invalid(message: String) extends ValidationResult
    case class Valid(checksum: Checksum) extends ValidationResult
  }

}


abstract class ChecksumHandler(val extension: String, val includeInResponseHeaders: Boolean) {

  lazy val extensionLc = extension.toLowerCase
  lazy val extensionLcWithDot = "." + extensionLc

  def digest(content: ZFileSystem.File): M[ChecksumHandler.DigestResults]

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

  def scrubChecksum(checksum: String): String =
    checksum
      .split(' ')
      .map(_.trim)
      .filter(_.length > 0)
      .headOption
      .getOrElse("")
      .toLowerCase

  def isChecksumValid(contentFile: ZFileSystem.File, checksumFile: ZFileSystem.File): M[ValidationResult] =
    for {
      rawExpectedChecksum <- checksumFile.readAsString
      actualDigestResults <- digest(contentFile)
    } yield {
      val expectedChecksum = scrubChecksum(rawExpectedChecksum)
      val actualChecksum = scrubChecksum(actualDigestResults.asHexString)
      if (actualChecksum === expectedChecksum) {
        ValidationResult.Valid(Checksum(extension, expectedChecksum))
      } else {
        ValidationResult.Invalid(s"checksum mismatch actual != expected -- ${actualChecksum} != ${expectedChecksum}")
      }
    }

  def resolveContentAsFile(contentPath: ContentPath, resolvedRepo: ResolvedRepo): M[Option[File]] =
    resolvedRepo
      .resolveContent(contentPath, false)
      .map {
        case Some(TempFile(file, _, _)) =>
          Some(file)
        case Some(CacheFile(file, _)) =>
          Some(file)
        case _ =>
          None
      }

}
