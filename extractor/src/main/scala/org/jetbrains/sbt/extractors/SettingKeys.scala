package org.jetbrains.sbt.extractors

import java.io.File
import sbt.SettingKey

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
object SettingKeys {
  val idePackagePrefix: SettingKey[Option[String]] = SettingKey[Option[String]]("ide-package-prefix")
  val ideBasePackages: SettingKey[Seq[String]] = SettingKey[Seq[String]]("ide-base-packages")
  val ideExcludedDirectories: SettingKey[Seq[File]] = SettingKey[Seq[File]]("ide-excluded-directories")
  val ideSkipProject: SettingKey[Boolean] = SettingKey[Boolean]("ide-skip-project")
  val ideOutputDirectory: SettingKey[Option[File]] = SettingKey[Option[File]]("ide-output-directory")

  val sbtIdeaBasePackage: SettingKey[Option[String]] = SettingKey[Option[String]]("idea-base-package")
  val sbtIdeaExcludeFolders: SettingKey[Seq[String]] = SettingKey[Seq[String]]("idea-exclude-folders")
  val sbtIdeaIgnoreModule: SettingKey[Boolean] = SettingKey[Boolean]("idea-ignore-module")

  /**
   * Mirrors the `monitoredScalaJSDirectories` sbt setting from `sbt-web-scalajs` plugin.
   *
   * @see https://github.com/vmunier/sbt-web-scalajs/blob/a3cf9ecc01506264929c463fe297ef2e11a3d439/src/main/scala/webscalajs/WebScalaJS.scala#L30
   */
  val monitoredScalaJSDirectories: SettingKey[Seq[File]] = SettingKey[Seq[File]]("monitoredScalaJSDirectories")
}


