package org.jetbrains

import _root_.sbt.ProjectRef

//import scala.language.implicitConversions

/**
 * @author Pavel Fatin
 * @author Nikolay Obedin
 */
package object sbt {
  implicit def `enrich Boolean`(b: Boolean) = new {
    def option[A](a: => A): Option[A] = if(b) Some(a) else None
  }

  implicit def `Fix Option.flatten on Scala 2.9.2`[T](option: Option[Option[T]]) = new {
    def flatten: Option[T] = option.flatMap(identity)
  }

  implicit def `enrich ProjectRef`(projectRef: ProjectRef) = new {
    def id: String = projectRef.project // TODO: append build url when IDEA-145101 is fixed
  }
}
