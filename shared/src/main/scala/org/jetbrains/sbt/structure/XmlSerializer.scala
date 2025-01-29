package org.jetbrains.sbt
package structure

import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.xml._

/**
 * @author Nikolay Obedin
 * @since 4/8/15.
 */
trait XmlSerializer[T] {
  def serialize(what: T): Elem
  def deserialize(what: Node): Either[Throwable,T]
}

object XmlSerializer {
  class DeserializableNode(node: Node) {
    def deserialize[T](implicit serializer: XmlSerializer[T]): Either[Throwable,T] =
      serializer.deserialize(node)
  }

  class DeserializableNodeSeq(nodeSeq: NodeSeq) {
    def deserialize[T](implicit serializer: XmlSerializer[T]): Seq[T] =
      nodeSeq.flatMap(_.deserialize[T].fold(_ => None, x => Some(x)))

    def deserializeOne[T](implicit serializer: XmlSerializer[T], manifest: ClassTag[T]): Either[Throwable,T] = {
      val ts = nodeSeq.map(_.deserialize[T]).collect { case Right(t) => t }
      if (ts.isEmpty)
        Left(new Error("None of " + manifest.runtimeClass.getSimpleName + " is found in " + nodeSeq))
      else if (ts.length > 1)
        Left(new Error("Multiple instances of " + manifest.runtimeClass.getSimpleName + " are found in " + nodeSeq))
      else
        Right(ts.head)
    }
  }

  class SerializableAny[T](obj: T) {
    def serialize(implicit serializer: XmlSerializer[T]): Elem =
      serializer.serialize(obj)
  }

  implicit def node2deserializableNode(node: Node): DeserializableNode =
    new DeserializableNode(node)

  implicit def nodeseq2deserializableNodeSeq(nodeSeq: NodeSeq): DeserializableNodeSeq =
    new DeserializableNodeSeq(nodeSeq)

  implicit def any2serializableAny[T](any: T): SerializableAny[T] =
    new SerializableAny[T](any)
}


