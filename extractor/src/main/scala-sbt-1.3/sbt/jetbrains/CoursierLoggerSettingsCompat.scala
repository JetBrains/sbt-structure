package sbt.jetbrains

import sbt.{Keys, Setting}

trait CoursierLoggerSettingsCompat extends Sbt1_3PlusUtils {
  lazy val artifactDownloadLoggerSettings: Seq[Setting[?]] = Seq(
    Keys.csrLogger := artifactDownloadCsrLogger.value
  )
}
