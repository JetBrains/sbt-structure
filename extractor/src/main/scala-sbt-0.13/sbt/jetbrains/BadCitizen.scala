package sbt.jetbrains

import sbt.{Command, SimpleCommand}

/**
  * The bad citizen accesses private[sbt] classes
  */
object BadCitizen {

  def isSimpleCommand(cmd: Command): Boolean =
    cmd.isInstanceOf[SimpleCommand]

}