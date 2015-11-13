package org.jetbrains.sbt

import sbt._
import structure._

/**
  * @author Nikolay Obedin
  * @since 11/10/15.
  */
object StructureKeys {
  lazy val dependencyConfigurations = SettingKey[Seq[sbt.Configuration]]("ss-dependency-configurations")
  lazy val sourceConfigurations     = SettingKey[Seq[sbt.Configuration]]("ss-source-configurations")
  lazy val testConfigurations       = SettingKey[Seq[sbt.Configuration]]("ss-test-configurations")
  lazy val acceptedProjects         = TaskKey[Seq[ProjectRef]]("ss-accepted-projects")
  lazy val sbtStructureOpts         = SettingKey[Options]("ss-options")

  lazy val extractPlay2           = TaskKey[Option[Play2Data]]("ss-extract-play-2")
  lazy val extractAndroid         = TaskKey[Option[AndroidData]]("ss-extract-android")
  lazy val extractBuild           = TaskKey[BuildData]("ss-extract-build")
  lazy val extractDependencies    = TaskKey[DependencyData]("ss-extract-dependencies")
  lazy val extractProject         = TaskKey[ProjectData]("ss-extract-project")
  lazy val extractProjects        = TaskKey[Seq[ProjectData]]("ss-extract-projects")
  lazy val extractRepository      = TaskKey[Option[RepositoryData]]("ss-extract-repository")

  lazy val sbtStructureOptions    = SettingKey[String]("sbt-structure-options")
  lazy val sbtStructureOutputFile = SettingKey[Option[File]]("sbt-structure-output-file")
  lazy val extractStructure       = TaskKey[StructureData]("extract-structure")
  lazy val dumpStructure          = TaskKey[Unit]("dump-structure")
}
