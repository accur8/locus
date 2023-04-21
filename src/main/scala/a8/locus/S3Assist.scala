package a8.locus


import a8.locus.Dsl.UrlPath
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{ListObjectsRequest, S3ObjectSummary}
import a8.locus.GenerateMavenMetadata.dateTime
import a8.locus.S3Assist.Entry.Directory
import a8.versions.Version
import cats.data.Chain
import SharedImports._
import scala.annotation.tailrec

object S3Assist {


  sealed trait Entry {
    lazy val name: String = path.last
    lazy val parent: UrlPath = path.parent
    val path: UrlPath
  }

  object Entry {

    case class S3Object(
      path: UrlPath,
      s3ObjectSummary: S3ObjectSummary,
    )(
      implicit amazonS3: AmazonS3
    ) extends Entry

    case class Directory(
      path: UrlPath,
      entries: Vector[Either[String,S3ObjectSummary]],
    )(
      implicit amazonS3: AmazonS3
    ) extends Entry

  }

  case class BucketName(value: String)

  def isDirectory(bucketName: BucketName, bucketPrefix: UrlPath)(implicit client: AmazonS3): Boolean = {
    val request = new ListObjectsRequest()
    request.setBucketName(bucketName.value)
    request.setPrefix(bucketPrefix.toString + "/")
    request.setMaxKeys(1)
    client.listObjects(request).getObjectSummaries.size() >= 1
  }

  def listDirectory(bucketName: BucketName, bucketPrefix: UrlPath)(implicit client: AmazonS3): Option[Directory] = {

    val entryName = bucketPrefix.last

    val prefix = bucketPrefix.withIsDirectory(isDirectory = false).toString

    @tailrec
    def impl(nextMarker: Option[String], accumulated: Vector[Either[String,S3ObjectSummary]]): Vector[Either[String,S3ObjectSummary]] = {

      val request = new ListObjectsRequest()
      request.setDelimiter("/")
      request.setMaxKeys(10000)
      request.setBucketName(bucketName.value)
      request.setPrefix(prefix + "/")
      nextMarker.foreach(request.setMarker)

      val response = client.listObjects(request)

      val resultsRight = response.getObjectSummaries.iterator().asScala.toVector.map(Right.apply)
      val resultsLeft = response.getCommonPrefixes.iterator().asScala.toVector.map(p => Left(UrlPath.parse(p).last))

      val results = resultsLeft ++ resultsRight
      val marker = response.getNextMarker
      if ( marker != null )
        impl(Some(marker), results)
      else
        results

    }

    val allEntries = impl(None, Vector.empty)
    if ( allEntries.nonEmpty ) {
      Some(Entry.Directory(bucketPrefix, allEntries))
    } else {
      None
    }

  }

}
