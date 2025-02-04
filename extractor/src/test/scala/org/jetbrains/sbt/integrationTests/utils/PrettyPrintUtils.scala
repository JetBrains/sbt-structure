package org.jetbrains.sbt.integrationTests.utils

object PrettyPrintUtils {

  def prettyPrintCaseClass(toPrint: Product): String = {
    val indentStep = "  "

    def print0(what: Any, indent: String): String = what match {
      case p: Product =>
        val prefix = indent + p.productPrefix
        if (p.productArity == 0)
          prefix
        else {
          val values = p.productIterator
            .map {
              case s: Seq[_] => s.map(x => print0(x, indent + indentStep)).mkString("\n")
              case pp: Product => print0(pp, indent + indentStep)
              case other => indent + indentStep + other.toString
            }
            .mkString("\n")
          s"""$prefix:
             |$values""".stripMargin.replace("\r", "")
        }
      case other =>
        indent + other.toString
    }

    print0(toPrint, indentStep)
  }
}
