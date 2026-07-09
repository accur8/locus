package a8.locus

import a8.locus.ziohttp.model.ContentPath
import org.scalatest.funspec.AnyFunSpec

/**
 * Regression: a request for the repo ROOT ("/repos/all/") produces an empty
 * ContentPath (parts == Nil). The content generators' canGenerateFor guards
 * used contentPath.last, which throws NoSuchElementException on an empty path,
 * surfacing as a 500 on the root directory listing. lastOption fixed it.
 */
class ContentGeneratorRootPathTest extends AnyFunSpec {

  private val root = ContentPath(Seq.empty, isDirectory = true)

  describe("canGenerateFor on the empty root ContentPath") {

    it("GenerateMavenMetadata does not throw and does not claim the root") {
      assert(!GenerateMavenMetadata.canGenerateFor(root))
    }

    it("GenerateIndexDotHtml does not throw (and generates for the root dir)") {
      // root is a directory, so the index-html generator legitimately handles it
      assert(GenerateIndexDotHtml.canGenerateFor(root))
    }

    it("ChecksumGenerator does not throw and does not claim the root") {
      assert(!ChecksumGenerator.canGenerateFor(root))
    }
  }

  describe("canGenerateFor still matches on real paths") {

    it("GenerateMavenMetadata matches a maven-metadata.xml leaf") {
      val p = ContentPath(Seq("io", "accur8", "godev", "a8", "maven-metadata.xml"), isDirectory = false)
      assert(GenerateMavenMetadata.canGenerateFor(p))
    }

    it("GenerateMavenMetadata is case-insensitive (preserved =:= behavior)") {
      val p = ContentPath(Seq("io", "accur8", "MAVEN-METADATA.XML"), isDirectory = false)
      assert(GenerateMavenMetadata.canGenerateFor(p))
    }
  }
}
