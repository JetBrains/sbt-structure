package sbt.jetbrains

import sbt.{Command, SimpleCommand}

import scala.util.control.NonFatal

/**
  * The bad citizen accesses private[sbt] classes
  */
object BadCitizen {

  def commandName(cmd: Command): Option[String] =
    try {
      cmd match {
        case simple: SimpleCommand => Option(simple.name)
        case _ => None
      }
    } catch {
      // guard against internal API changes
      case NonFatal(x) => None
    }

}
