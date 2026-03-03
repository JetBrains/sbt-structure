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
   * @see SCL-24518
   */
  val monitoredScalaJSDirectories: SettingKey[Seq[File]] = SettingKey[Seq[File]]("monitoredScalaJSDirectories")

  /**
   * The `bspEnabled` key is available in sbt 1.4+.
   * We don't have separate sources for 1.4+ compilation,
   * so this is just a mirror of the original sbt setting that allows us to check its value in all sbt 1 & 2 projects (if it is not available, it will be `true`).
   *
   * @see https://youtrack.jetbrains.com/issue/SCL-24744
   * @see the same trick is used e.g., in https://github.com/sbt/sbt-jmh/blob/d85545d85a448fd89c8a6355e682c236f6d76705/plugin/src/main/scala/pl/project13/scala/sbt/JmhPlugin.scala#L77
   */
  val bspEnabled: SettingKey[Boolean] = SettingKey[Boolean]("bspEnabled")
}


