package sbt.jetbrains

import sbt.{Def, Keys, Setting}

trait CoursierLoggerSettingsCompat extends CoursierLoggerTaskDefinition {
  lazy val artifactDownloadLoggerSettings: Seq[Setting[?]] = Seq(
    Keys.csrLogger := Def.uncached(artifactDownloadCsrLogger.value)
  )
}
