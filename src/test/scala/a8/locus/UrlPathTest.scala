package a8.locus

import a8.locus.Dsl.UrlPath
import org.scalatest.funspec.AnyFunSpec

class UrlPathTest extends AnyFunSpec {

  describe("parse") {

    def test(input: String, expected: Dsl.UrlPath): Unit = {
      val actual = Dsl.UrlPath.parse(input)
      assert(expected === actual): @scala.annotation.nowarn
    }

    test(
      "aa/bb/cc",
      UrlPath(Iterable("aa", "bb", "cc"), false)
    )

    test(
      "aa/BB/cc",
      UrlPath(Iterable("aa", "BB", "cc"), false)
    )

    test(
      "https://locus.accur8.io/repos/all/io/get-coursier/coursier_2.12/2.0.0-RC6/index.html",
      UrlPath(Iterable("https:", "locus.accur8.io", "repos", "all", "io", "get-coursier", "coursier_2.12", "2.0.0-RC6", "index.html"), false)
    )

    test(
      "https://locus.accur8.io/repos/all/io/get-coursier/coursier_2.12/2.0.0-rc6/index.html",
      UrlPath(Iterable("https:", "locus.accur8.io", "repos", "all", "io", "get-coursier", "coursier_2.12", "2.0.0-rc6", "index.html"), false)
    )

  }

  describe("toString") {

    def test(input: String, expected: String): Unit = {
      val actual = Dsl.UrlPath.parse(input).toString
      assert(actual === expected): @scala.annotation.nowarn
    }

    test(
      "aa/bb/cc",
      "aa/bb/cc"
    )

    test(
      "aa/BB/cc",
      "aa/BB/cc",
    )

    test(
      "https://locus.accur8.io/repos/all/io/get-coursier/coursier_2.12/2.0.0-RC6/index.html",
      "https:/locus.accur8.io/repos/all/io/get-coursier/coursier_2.12/2.0.0-RC6/index.html"
    )

    test(
      "https://locus.accur8.io/repos/all/io/get-coursier/coursier_2.12/2.0.0-rc6/index.html",
      "https:/locus.accur8.io/repos/all/io/get-coursier/coursier_2.12/2.0.0-rc6/index.html"
    )

  }

}
