package sbt.jetbrains

import sbt.Keys.csrLogger
import sbt._

object LogDownloadArtifacts {

  lazy val globalSettings: Seq[Setting[_]] = Seq[Setting[_]](
    csrLogger := keysAdapterEx.artifactDownloadCsrLogger.value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq.empty
}
