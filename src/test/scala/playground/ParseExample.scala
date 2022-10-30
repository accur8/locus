package playground

import a8.versions.Version

object ParseExample {

  def main(args: Array[String]): Unit = {
    val v = Version.parse("1.0.0-20200615_1710_master")
    v.toString
  }

}
