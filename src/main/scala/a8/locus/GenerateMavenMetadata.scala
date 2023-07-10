package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry, RepoContent}
import a8.locus.S3Assist.Entry
import a8.locus.model.DateTime
import a8.shared.jdbcf.querydsl.QueryDsl.valueToConstant
import a8.versions.{BuildTimestamp, ParsedVersion, VersionParser}
import cats.data.Chain
import SharedImports.*
import a8.locus.ziohttp.model.*

import java.time.Month

object GenerateMavenMetadata extends ContentGenerator {

  def dateTime(bi: BuildTimestamp): DateTime =
    DateTime(
      bi.year,
      Month.of(bi.month),
      bi.day,
      bi.hour,
      bi.minute,
      bi.second.getOrElse(0)
    )


  override def canGenerateFor(contentPath: ContentPath): Boolean =
    contentPath.last =:= "maven-metadata.xml"


  override def generate(context: String, contentPath: ContentPath, resolvedRepo: ResolvedRepo): M[Option[RepoContent]] = {
    resolvedRepo.entries(contentPath.parent).map { entriesOpt =>
      entriesOpt.map { entries =>
        val sortedEntries =
          entries
            .iterator
            .filter(_.isDirectory)
            .flatMap { entry =>
              val name = entry.name
              ParsedVersion
                .parse(name)
                .toOption
                .map { v =>
                  (
                    v,
                    v.buildInfo.map(bi => dateTime(bi.buildTimestamp)).getOrElse(DateTime.empty),
                    name,
                  )
                }
            }
            .toList
            .sorted

        val artifactId = contentPath.last
        val groupId = contentPath.parent.fullPath

        val latest = sortedEntries.last._3
        val lastUpdated: DateTime = sortedEntries.last._2
        val lastUpdatedStr: String = f"${lastUpdated.year}%04d${lastUpdated.month.getValue}%02d${lastUpdated.day}%02d${lastUpdated.hour}%02d${lastUpdated.minute}%02d${lastUpdated.second}%02d" // ??? // 20200529170049

        RepoContent.generateHtml(
          resolvedRepo,
          s"""<?xml version="1.0" encoding="UTF-8"?>

  <metadata modelVersion="1.1.0">
    <groupId>${groupId}</groupId>
    <artifactId>${artifactId}</artifactId>
    <version>${latest}</version>
    <versioning>
      <latest>${latest}</latest>
      <release>${latest}</release>
      <versions>
  ${sortedEntries.map(v => s"      <version>${v._3}</version>").mkString("\n")}
      </versions>
    </versioning>
    <lastUpdated>${lastUpdatedStr}</lastUpdated>
  </metadata>
        """.trim
        )
      }
    }
  }

  override def extraEntries(entries: Iterable[DirectoryEntry], resolvedRepo: ResolvedRepo): Iterable[DirectoryEntry] =
    Iterable.empty

}
