package a8.locus

object UrlPathDemo extends App {


  val path = Dsl.UrlPath.parse("https://locus.accur8.io/repos/all/io/get-coursier/coursier_2.12/2.0.0-RC6/index.html")

  println(path)

}
