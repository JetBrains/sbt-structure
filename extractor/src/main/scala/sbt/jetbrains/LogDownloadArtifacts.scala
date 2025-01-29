package sbt.jetbrains

import org.jetbrains.sbt.SbtStateOps
import sbt.*
import sbt.jetbrains.PluginCompat.*

//NOTE: this class is not directly used in sbt-structure sources, but it's used in Scala Plugin
object LogDownloadArtifacts extends (State => State) with SbtStateOps {
  def apply(state: State): State = {
    val settings = sbt.jetbrains.PluginCompat.artifactDownloadLoggerSettings.toImmutableSeq
    applySettings(state, settings, Seq[Setting[_]]())
  }
}
