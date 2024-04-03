package sbt.jetbrains

import sbt._
import org.jetbrains.sbt.{CreateTasks, SbtStateOps}

object LogDownloadArtifacts extends (State => State) with SbtStateOps {

  def apply(state: State): State =
    CreateTasks.applySettings(state, Seq[Setting[_]](), Seq[Setting[_]]())
}
