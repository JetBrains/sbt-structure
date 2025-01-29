package sbt.jetbrains

import sbt._
import org.jetbrains.sbt.SbtStateOps

//NOTE: this class is not directly used in sbt-structure sources, but it's used in Scala Plugin
object LogDownloadArtifacts extends (State => State) with SbtStateOps {

  def apply(state: State): State =
    applySettings(state, Seq[Setting[_]](), Seq[Setting[_]]())
}
