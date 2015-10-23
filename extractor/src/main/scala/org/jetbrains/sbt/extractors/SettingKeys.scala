package org.jetbrains.sbt.extractors

import java.io.File
import sbt.SettingKey

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
object SettingKeys {
  val ideBasePackages         = SettingKey[Seq[String]]("ide-base-packages")
  val ideExcludedDirectories  = SettingKey[Seq[File]]("ide-excluded-directories")
  val ideSkipProject          = SettingKey[Boolean]("ide-skip-project")
  val ideOutputDirectory      = SettingKey[Option[File]]("ide-output-directory")

  val sbtIdeaBasePackage      = SettingKey[Option[String]]("idea-base-package")
  val sbtIdeaExcludeFolders   = SettingKey[Seq[String]]("idea-exclude-folders")
  val sbtIdeaIgnoreModule     = SettingKey[Boolean]("idea-ignore-module")
}


