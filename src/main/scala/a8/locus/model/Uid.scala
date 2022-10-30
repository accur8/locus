package a8.locus.model

case class Uid(value: String) extends Ordered[Uid] {
  override def compare(that: Uid): Int = {
    value.compareTo(that.value)
  }
}
