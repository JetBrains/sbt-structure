package sbt.jetbrains

object SeqOpsCompat extends SeqOpsCompat

/**
 * These utility methods are required to make work cross-compilation between Scala 2.12 (sbt 1.x) and Scala 3 (sbt 2.x).
 * In Scala 2.12 the default type of Seq was scala.collection.Seq, but in Scala 2.13 & Scala 3 it's scala.collection.immutable.Seq
 */
trait SeqOpsCompat {
  import scala.language.implicitConversions

  implicit def seqToImmutableSeq[T](seq: scala.collection.Seq[T]): scala.collection.immutable.Seq[T] = {
    val builder = scala.collection.immutable.Seq.newBuilder[T]
    builder ++= seq
    builder.result
  }

  implicit class SeqOps[T](private val value: scala.collection.Seq[T]) {
    def toSbtSeqType: scala.collection.Seq[T] = value
    def toImmutableSeq: scala.collection.immutable.Seq[T] = value.to[scala.collection.immutable.Seq]
  }
}

