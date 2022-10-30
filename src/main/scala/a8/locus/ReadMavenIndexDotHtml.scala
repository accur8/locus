package a8.locus

import java.io.StringReader
import org.htmlcleaner.{ContentNode, HtmlCleaner, TagNode}

import scala.xml.XML
import a8.locus.ResolvedModel.{DirectoryEntry, ResolvedRepo}
import SharedImports._
import a8.locus.model.DateTime

object ReadMavenIndexDotHtml {


  def main(args: Array[String]): Unit = {

    // gotten from https://repo1.maven.org/maven2/com/twitter/common/collections/0.0.111/

    val html =
      """

<!DOCTYPE html>
<html>

<head>
	<title>Central Repository: com/twitter/common/collections</title>
	<meta name="viewport" content="width=device-width, initial-scale=1.0">
	<style>
body {
	background: #fff;
}
	</style>
</head>

<body>
	<header>
		<h1>com/twitter/common/collections</h1>
	</header>
	<hr/>
	<main>
		<pre id="contents">
<a href="../">../</a>
<a href="0.0.110/" title="0.0.110/">0.0.110/</a>                                          2016-10-07 01:28         -
<a href="0.0.111/" title="0.0.111/">0.0.111/</a>                                          2017-02-27 22:48         -
<a href="maven-metadata.xml" title="maven-metadata.xml">maven-metadata.xml</a>                                2017-03-03 04:31       375
<a href="maven-metadata.xml.md5" title="maven-metadata.xml.md5">maven-metadata.xml.md5</a>                            2017-03-03 04:31        32
<a href="maven-metadata.xml.sha1" title="maven-metadata.xml.sha1">maven-metadata.xml.sha1</a>                           2017-03-03 04:31        40
		</pre>
	</main>
	<hr/>
</body>

</html>
     """.trim


    parse(html, null).foreach(println)

  }

  def parse(html: String, rr: ResolvedRepo, parseError: (String, Boolean, List[String]) => Unit = (_,_, _) => ()): Vector[DirectoryEntry] = {
    val cleaner = new HtmlCleaner
    val rootNode = cleaner.clean(new StringReader(html))
    rootNode.getElementsByName("a", true).headOption.toVector.flatMap { headElem =>
      val children = headElem.getParent.getAllChildren.asScala
      val pairs: Seq[(String, Boolean, List[String])] =
        children.zip(children.drop(1)).toSeq.flatMap {
          case (tagNode: TagNode, cn: ContentNode) =>
            val rawName = tagNode.getAttributeByName("href")
            val directory = rawName.endsWith("/")
            val name =
              if ( directory ) rawName.substring(0, rawName.length-1)
              else rawName
            Some((name, directory, cn.getContent.trim.splitList(" ")))
          case _ =>
            None
        }
      pairs.flatMap {
        case ("..", true, List()) =>
          None
        case (name, isDirectory, List(date, time, "-")) =>
          Some(DirectoryEntry(name, isDirectory, rr, Some(DateTime.uberParse(date + " " + time))))
        case (name, isDirectory, List("-", "-")) =>
          Some(DirectoryEntry(name, isDirectory, rr, None))
        case (name, isDirectory, List(date, time, size)) =>
          Some(DirectoryEntry(name, isDirectory, rr, Some(DateTime.uberParse(date + " " + time)), Some(size.toLong)))
        case t =>
          parseError(t._1, t._2, t._3)
          None
      }
    }
  }

}
