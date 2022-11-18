
// 
// DO NOT EDIT THIS FILE IT IS MACHINE GENERATED
// 
// This file is generated from modules.conf using `a8-versions build_dot_sbt`
// 
// It was generated at 2022-10-25T16:23:23.752883 by raph on ENNS-WORK
// 
// a8-versions build/versioning info follows
// 
//        build_date : Tue Oct 25 06:45:32 EDT 2022
//        build_machine : stella.local
//        build_user : glen
// 
//      

val appVersion = a8.sbt_a8.versionStamp(file("."))

val scalaLibVersion = "2.13.10"
val versionsVersion = "1.0.0-20221113_1354_master"
val amazonVersion = "1.12.196"
val undertowVersion = "2.0.15.Final"
val model3Version = "2.7.1-20210602_1321_master"

scalacOptions in Global ++= Seq("-deprecation", "-unchecked", "-feature")


//resolvers in Global += "a8-repo" at Common.readRepoUrl()
//publishTo in Global := Some("a8-repo-releases" at Common.readRepoUrl())
publishTo in Global := sonatypePublishToBundle.value
credentials in Global += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")

scalaVersion in Global := scalaLibVersion

organization in Global := "io.accur8"

version in Global := appVersion

versionScheme in Global := Some("strict")

serverConnectionType in Global := ConnectionType.Local


lazy val locus =
  Common
    .jvmProject("a8-locus", file("../locus"), "locus")
    .settings(
      libraryDependencies ++= Seq(
        "io.accur8" %% "a8-versions" % versionsVersion % "compile",
        "org.scalatest" %% "scalatest" % "3.2.14" % Test,
        "io.undertow" % "undertow-core" % undertowVersion,
        "com.amazonaws" % "aws-java-sdk-s3" % amazonVersion % "compile",
        "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.24",
        "jakarta.xml.bind" % "jakarta.xml.bind-api" % "2.3.2",
        "org.glassfish.jaxb" % "jaxb-runtime" % "2.3.2",
      )
    )


   