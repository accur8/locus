package a8.locus

import a8.versions.Build.BuildType
import a8.versions.{RepositoryOps, Version}
import coursier.ModuleName
import coursier.core.Organization

object ListDependenciesDemo {


  def main(args: Array[String]): Unit = {

    implicit val buildType = BuildType.ArtifactoryBuild

    val tree =
      RepositoryOps
        .default
        .resolveDependencyTree(
          coursier.core.Module(Organization("a8"), ModuleName("a8-qubes-dist_2.12"), Map()),
          Version.parse("2.7.0-20180418_0536_master").get
        )

    val searchStr = "scala-compiler"

    val urls =
      tree
        .resolution
        .artifacts()
        .map(_.url)

    println(urls.mkString("\n"))

    val artifacts = tree.resolution.artifacts().filter(_.url.contains(searchStr))

    val errors = tree.resolution.errors

    val files = tree.localArtifacts.sortBy(_.getCanonicalPath)

    val lefts = tree.rawLocalArtifacts.filter(_.isLeft)
    val rights = tree.rawLocalArtifacts.filter(_.isRight)

    val scalaps = files.filter(_.getCanonicalPath.contains(searchStr))

    toString

  }
}
