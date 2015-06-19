package org.jetbrains.sbt.extractors

import java.io.File

import sbt.SettingKey

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
object SettingKeys {
  val ideBasePackages         = SettingKey[Seq[String]]("ide-base-packages")
  val sbtIdeaBasePackage      = SettingKey[Option[String]]("idea-base-package")
  val ideExcludedDirectories  = SettingKey[Seq[File]]("ide-excluded-directories")
  val sbtIdeaExcludeFolders   = SettingKey[Seq[String]]("idea-exclude-folders")
}


