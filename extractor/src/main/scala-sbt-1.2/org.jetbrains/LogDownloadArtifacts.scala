package sbt.jetbrains

import sbt._
import org.jetbrains.sbt.SbtStateOps

object LogDownloadArtifacts extends (State => State) with SbtStateOps {

  def apply(state: State): State =
    applySettings(state, Seq[Setting[_]](), Seq[Setting[_]]())
}
