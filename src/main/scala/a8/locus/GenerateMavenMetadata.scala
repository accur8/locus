package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.ResolvedModel.{ContentGenerator, DirectoryEntry, ResolvedS3Repo}
import a8.locus.S3Assist.Entry
import a8.locus.model.DateTime
import a8.shared.jdbcf.querydsl.QueryDsl.valueToConstant
import a8.versions.{BuildTimestamp, Version, VersionParser}
import cats.data.Chain
import SharedImports.*
import a8.locus.ziohttp.model.{HttpResponse, HttpResponseBody}

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


  override def canGenerateFor(urlPath: UrlPath): Boolean =
    urlPath.last =:= "maven-metadata.xml"

  override def generate(urlPath: UrlPath, resolvedRepo: ResolvedModel.ResolvedRepo): Option[HttpResponse] = {

    resolvedRepo.entries(urlPath.parent).flatMap { entries =>

      val sortedEntriesOpt =
        entries
          .iterator
          .filter(_.isDirectory)
          .flatMap { entry =>
            val name = entry.name
            Version
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
          .toNonEmpty

      sortedEntriesOpt.map { sortedEntries =>
        val artifactId = urlPath.last
        val groupId = urlPath.parent.toString

        val latest = sortedEntries.last._3
        val lastUpdated: DateTime = sortedEntries.last._2
        val lastUpdatedStr: String = f"${lastUpdated.year}%04d${lastUpdated.month.getValue}%02d${lastUpdated.day}%02d${lastUpdated.hour}%02d${lastUpdated.minute}%02d${lastUpdated.second}%02d" // ??? // 20200529170049

        HttpResponseBody.xml(
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


}
