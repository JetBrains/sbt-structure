package sbt.jetbrains

import sbt.{Def, Keys, Setting}

trait CoursierLoggerSettingsCompat extends Sbt1_3PlusUtils {
  lazy val artifactDownloadLoggerSettings: Seq[Setting[?]] = Seq(
    Keys.csrLogger := Def.uncached(artifactDownloadCsrLogger.value)
  )
}
