package sbt.jetbrains

import sbt.{Keys, Setting}

trait CoursierLoggerSettingsCompat extends CoursierLoggerTaskDefinition {
  lazy val artifactDownloadLoggerSettings: Seq[Setting[?]] = Seq(
    Keys.csrLogger := artifactDownloadCsrLogger.value
  )
}
