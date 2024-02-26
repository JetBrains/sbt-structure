package org.jetbrains

import _root_.sbt.{Configuration, ProjectRef}

import scala.xml.{Elem, NamespaceBinding, Node, PrettyPrinter}

/**
 * @author Pavel Fatin
 * @author Nikolay Obedin
 */
package object sbt {
  var MaxXmlWidthInTests: Option[Int] = None

  def newXmlPrettyPrinter: PrettyPrinter = new PrettyPrinter(MaxXmlWidthInTests.getOrElse(180), 2) {
    override protected def traverse(node: Node, pscope: NamespaceBinding, ind: Int): Unit = {
      import org.jetbrains.sbt.structure.DataSerializers._

      node match {
        case _: Elem =>
          node.label match {
            /**
             * Ensure these elements are located on new lines for a nicer output, especially in test data
             */
            case ImportElementName |
                 ClassesElementName |
                 DocsElementName |
                 SourcesElementName =>
              //Force new line to be inserted under the hood.
              //We can't use max int value in order there is no data overflow under the hood inside traverse.
              //At the same time we expect no element value to be longer then half of the int max value.
              cur = Int.MaxValue / 2
            case _ =>
          }
        case _ =>
      }

      super.traverse(node, pscope, ind)
    }
  }

  implicit def `enrich Boolean`(b: Boolean) = new {
    def option[A](a: => A): Option[A] = if(b) Some(a) else None
  }

  implicit def `Fix Option.flatten on Scala 2.9.2`[T](option: Option[Option[T]]) = new {
    def flatten: Option[T] = option.flatMap(identity)
  }

  implicit def `enrich ProjectRef`(projectRef: ProjectRef) = new {
    def id: String = projectRef.project // TODO: append build url when IDEA-145101 is fixed
  }

  /** Transitive hull of configs that a config extends. */
  @scala.annotation.tailrec
  def transitiveExtends(configs: Seq[Configuration]): Seq[Configuration] = {
    val extended = (configs.flatMap(_.extendsConfigs) ++ configs).distinct
    if (extended.map(_.name) == configs.map(_.name)) extended
    else transitiveExtends(extended)
  }
}
