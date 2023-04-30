package a8.locus


import a8.locus.Dsl.UrlPath
import a8.locus.GenerateMavenMetadata.dateTime
import a8.locus.S3Assist.Entry.{Directory, S3Object}
import a8.versions.ParsedVersion
import cats.data.Chain

import scala.annotation.tailrec
import SharedImports.*
import software.amazon.awssdk.services.s3.S3Client
import zio.s3.{ListObjectOptions, S3ObjectSummary}
import ziohttp.model.{M, *}

object S3Assist {


  sealed trait Entry {
    lazy val name: String = path.last
    lazy val parent: ContentPath = path.parent
    val path: ContentPath
  }

  object Entry {

    case class S3Object(
      path: ContentPath,
      s3ObjectSummary: software.amazon.awssdk.services.s3.model.S3Object,
    ) extends Entry

    case class Directory(
      path: ContentPath,
      entries: Vector[Either[String,S3ObjectSummary]],
    ) extends Entry

  }

  case class BucketName(value: String)

  def isDirectory(bucketName: BucketName, contentPath: ContentPath): M[Boolean] = {
    val bucketPrefix = contentPath.asDirectory.fullPath
    zio.s3.listObjects(bucketName.value, ListObjectOptions(Some(bucketPrefix), maxKeys = 1, delimiter = Some("/"), starAfter = None))
      .map(_.objectSummaries.nonEmpty)
  }

  def list2(bucketName: BucketName, dir: ContentPath) = {
    import software.amazon.awssdk.services.s3
    val prefix = dir.asDirectory.fullPath
    zservice[S3Client].flatMap { s3Client =>
      zblock {

        val request =
          s3.model.ListObjectsV2Request
            .builder
            .bucket(bucketName.value)
            .maxKeys(10 * 1024)
            .prefix(prefix)
            .delimiter("/")
            .build

        s3Client
          .listObjectsV2Paginator(request)
          .iterator()
          .asScala
          .foldLeft(Vector.empty[Either[String, s3.model.S3Object]])((vect, response) =>
            vect ++
              response
                .commonPrefixes()
                .asScala
                .map { cp =>
                  val p = cp.prefix()
                  Left(
                    p.substring(prefix.length, p.length - 1)
                  )
                } ++
              response
                .contents()
                .asScala
                .map(s3o =>
                  Right(s3o)
                )
          )
      }
    }

  }

//  def listDirectory(bucketName: BucketName, dir: ContentPath): M[Option[Directory]] = {
//    val bucketPrefix = dir.asFile.fullPath + "/"
//    zio.s3.listObjects(bucketName.value, ListObjectOptions(Some(bucketPrefix), maxKeys = 10*1024, delimiter = Some("/"), starAfter = None))
////    zio.s3.listObjects(bucketName.value, ListObjectOptions(Some(bucketPrefix), maxKeys = 10*1024, delimiter = Some("/"), starAfter = None))
////      .runCollect
//      .map { result =>
//        if (result.objectSummaries.nonEmpty) {
//          Some(Entry.Directory(dir, result.objectSummaries.toVector))
//        } else {
//          None
//        }
//      }
//      .either
//      .flatMap {
//        case Right(o) =>
//          zsucceed(o)
//        case Left(e: software.amazon.awssdk.services.s3.model.S3Exception) if e.statusCode() == 404 =>
//          zsucceed(None)
//        case Left(e) =>
//          zfail(e)
//      }
//
//  }

  def handleNotFound[A](effect: M[A]): M[Option[A]] =
    effect
      .either
      .flatMap {
        case Right(o) =>
          zsucceed(Some(o))
        case Left(e: software.amazon.awssdk.services.s3.model.S3Exception) if e.statusCode() == 404 =>
          zsucceed(None)
        case Left(e) =>
          zfail(e)
      }

}
