package a8.locus

import a8.locus.Dsl.UrlPath
import a8.locus.ChecksumHandler.ValidationResult
import a8.locus.ResolvedModel.RepoContent.{CacheFile, GeneratedContent, TempFile}
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry, RepoContent}
import a8.locus.SharedImports.*
import a8.locus.model.DateTime
import a8.locus.ziohttp.model.*
import a8.shared.{FileSystem, ZFileSystem}
import a8.shared.ZFileSystem.File
import a8.versions.{BuildTimestamp, ParsedVersion}
import org.apache.commons.codec.digest.DigestUtils
import zio.{Chunk, ZIO}

import java.time.Month

object ChecksumGenerator extends ContentGenerator {

  override def canGenerateFor(contentPath: ContentPath): Boolean =
    contentPath.parts.lastOption.exists { filename =>
      val filenameLc = filename.toLowerCase
      ChecksumHandler
        .all
        .exists(checksum => filenameLc.endsWith(checksum.extensionLc))
    }

  def checksumForPath(path: ContentPath, resolvedRepo: ResolvedRepo): Option[ChecksumHandler] =
    path
      .parts
      .lastOption
      .flatMap(filename =>
        val filenameLc = filename.toLowerCase
        resolvedRepo
          .generatedChecksumHandlers
          .find(cs => filenameLc.endsWith(cs.extensionLc))
      )

  override def generate(context: String, checksumPath: ContentPath, resolvedRepo: ResolvedRepo): M[Option[RepoContent]] = {
    val effectOpt: Option[ZIO[Env, Throwable, Option[RepoContent]]] =
      checksumForPath(checksumPath, resolvedRepo).flatMap { checksumHandler =>
        checksumPath.dropExtension.map { basePath =>
          resolveContentAsFile(basePath, resolvedRepo).flatMap {
            case None =>
              zsucceed(None)
            case Some(contentFile) =>
              ChecksumHandler
                .Sha256
                .digest(contentFile)
                .map(digest =>
                  GeneratedContent(
                    resolvedRepo,
                    contentType = None,
                    content = digest.asHexString,
                  ).some
                )
          }
        }
      }
    effectOpt.getOrElse(zsucceed(None))
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

  def canGenerateFor(directoryEntry: DirectoryEntry): Boolean = {
    val name = directoryEntry.name.toLowerCase
    name.endsWith(".jar") || name.endsWith(".pom")
  }

  override def extraEntries(entries: Iterable[DirectoryEntry], resolvedRepo: ResolvedRepo): Iterable[DirectoryEntry] = {
    val entriesByName = entries.map(_.name).toSet
    entries
      .filter(canGenerateFor)
      .flatMap { entry =>
        val checksumHandlers = (entry.resolvedRepo.generatedChecksumHandlers ++ resolvedRepo.generatedChecksumHandlers).distinct
        checksumHandlers
          .map { ch =>
            entry
              .copy(
                name = entry.name + "." + ch.extension,
                size = Some(64),
                generated = true,
                directUrl = None,
              )
          }
          .filterNot(e => entriesByName.contains(e.name))
      }
  }

}
