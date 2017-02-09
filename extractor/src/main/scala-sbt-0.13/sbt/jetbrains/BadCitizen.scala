package sbt.jetbrains

import sbt.{Command, SimpleCommand}

/**
  * The bad citizen accesses private[sbt] classes
  */
object BadCitizen {

  def commandName(cmd: Command): Option[String] =
    cmd match {
      case simple: SimpleCommand => Option(simple.name)
      case _ => None
    }

}
