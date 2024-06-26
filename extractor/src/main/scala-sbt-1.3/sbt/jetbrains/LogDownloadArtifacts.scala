package sbt.jetbrains

import org.jetbrains.sbt.SbtStateOps
import sbt.Keys.csrLogger
import sbt._

object LogDownloadArtifacts extends (State => State) with SbtStateOps {

  lazy val globalSettings: Seq[Setting[_]] = Seq[Setting[_]](
    csrLogger := keysAdapterEx.artifactDownloadCsrLogger.value
  )

  def apply(state: State): State =
    applySettings(state, globalSettings, Seq[Setting[_]]())
}
