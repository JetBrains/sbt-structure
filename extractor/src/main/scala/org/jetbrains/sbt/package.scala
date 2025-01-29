package org.jetbrains

import _root_.sbt.{Configuration, ProjectRef, Reference, Scope, Select, Zero}

import scala.language.implicitConversions
import scala.xml.{Elem, NamespaceBinding, Node, PrettyPrinter}

package object sbt {
  var MaxXmlWidthInTests: Option[Int] = None

  def newXmlPrettyPrinter: PrettyPrinter = new PrettyPrinter(MaxXmlWidthInTests.getOrElse(180), 2) {
    override protected def traverse(node: Node, pscope: NamespaceBinding, ind: Int): Unit = {
      import org.jetbrains.sbt.structure.DataSerializers.*

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

  final implicit class BooleanOps(val b: Boolean) extends AnyVal {
    def option[A](a: => A): Option[A] = if (b) Some(a) else None
  }

  final implicit class OptionOps[T](val option: Option[Option[T]]) extends AnyVal {
    def flatten: Option[T] = option.flatMap(identity)
  }

  final implicit class ProjectRefOps(val projectRef: ProjectRef) extends AnyVal {
    def id: String = projectRef.project // TODO: append build url when IDEA-145101 is fixed
  }

  /** Transitive hull of configs that a config extends. */
  @scala.annotation.tailrec
  def transitiveExtends(configs: Seq[Configuration]): Seq[Configuration] = {
    val extended = (configs.flatMap(_.extendsConfigs) ++ configs).distinct
    if (extended.map(_.name) == configs.map(_.name)) extended
    else transitiveExtends(extended)
  }


  // copied from sbt.internal.Load
  def projectScope(project: Reference): Scope =
    Scope(Select(project), Zero, Zero, Zero)
}
