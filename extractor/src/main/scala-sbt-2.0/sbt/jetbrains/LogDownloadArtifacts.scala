package sbt.jetbrains

import org.jetbrains.sbt.SbtStateOps
import sbt.*
import sbt.Keys.csrLogger

//NOTE: this class is not directly used in sbt-structure sources, but it's used in Scala Plugin
object LogDownloadArtifacts extends (State => State) with SbtStateOps {

  lazy val globalSettings: Seq[Setting[_]] = Seq[Setting[_]](
    csrLogger := keysAdapterEx.artifactDownloadCsrLogger.value
  )

  def apply(state: State): State =
    applySettings(state, globalSettings, Seq[Setting[_]]())
}
