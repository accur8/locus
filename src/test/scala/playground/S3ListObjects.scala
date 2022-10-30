package playground


import a8.locus.{Config, GenerateMavenMetadata, S3Assist}
import a8.locus.Dsl.UrlPath
import a8.locus.S3Assist.{BucketName, Entry}
import a8.locus.model.DateTime
import a8.shared.ConfigMojo
import a8.versions.{BuildTimestamp, Version, VersionParser}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

import java.time.Month

object S3ListObjects {


  lazy val serverConfig: Config.LocusConfig =
    ConfigMojo()
      .locus
      .serer
      .as[Config.LocusConfig]

  implicit lazy val amazonClient: AmazonS3 =
    AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(serverConfig.s3.get.accessKey, serverConfig.s3.get.secretKey)))
      .withRegion(com.amazonaws.regions.Regions.US_EAST_1)
      .build()

  def dateTime(bi: BuildTimestamp): DateTime =
    DateTime(
      bi.year,
      Month.of(bi.month),
      bi.day,
      bi.hour,
      bi.minute,
      bi.second.getOrElse(0)
    )

  def run = {

//    val prefixStr = "foo/bar"
//    val prefixStr = "legacy/libs-releases-local/a8/a8-versions_2.12/index.html"
    val prefixStr = "legacy/libs-releases-local/a8/a8-versions_2.12/"

    val bucket = BucketName("a8-artifacts")
    val path = UrlPath.parse(prefixStr)

    println(S3Assist.isDirectory(bucket, path))

    val result = S3Assist.listDirectory(bucket, path)

    println(S3Assist.isDirectory(bucket, path))

    toString

//    println(GenerateMavenMetadata(UrlPath.parse("a8/a8-versions_2.12"), entries))

  }


}
