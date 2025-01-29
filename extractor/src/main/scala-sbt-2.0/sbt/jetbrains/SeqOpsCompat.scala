package sbt.jetbrains

object SeqOpsCompat extends SeqOpsCompat

trait SeqOpsCompat:
  import scala.language.implicitConversions

  implicit def seqToImmutableSeq[T](seq: scala.collection.Seq[T]): scala.collection.immutable.Seq[T] = {
    val builder = scala.collection.immutable.Seq.newBuilder[T]
    builder ++= seq
    builder.result
  }

  extension [T](value: scala.collection.Seq[T])
    def toSbtSeqType: scala.collection.immutable.Seq[T] = value.to(scala.collection.immutable.Seq)
    def toImmutableSeq: scala.collection.immutable.Seq[T] = value.to(scala.collection.immutable.Seq)

