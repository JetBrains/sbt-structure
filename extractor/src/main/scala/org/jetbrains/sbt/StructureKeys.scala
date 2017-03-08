package org.jetbrains.sbt

import sbt.{Configuration, _}
import structure._

/**
  * @author Nikolay Obedin
  * @since 11/10/15.
  */
object StructureKeys {
  lazy val dependencyConfigurations: SettingKey[Seq[Configuration]] = SettingKey[Seq[sbt.Configuration]]("ss-dependency-configurations")
  lazy val sourceConfigurations: SettingKey[Seq[Configuration]] = SettingKey[Seq[sbt.Configuration]]("ss-source-configurations")
  lazy val testConfigurations: SettingKey[Seq[Configuration]] = SettingKey[Seq[sbt.Configuration]]("ss-test-configurations")
  lazy val acceptedProjects: TaskKey[Seq[ProjectRef]] = TaskKey[Seq[ProjectRef]]("ss-accepted-projects")
  lazy val sbtStructureOpts: SettingKey[Options] = SettingKey[Options]("ss-options")

  lazy val extractPlay2: TaskKey[Option[Play2Data]] = TaskKey[Option[Play2Data]]("ss-extract-play-2")
  lazy val extractAndroid: TaskKey[Option[AndroidData]] = TaskKey[Option[AndroidData]]("ss-extract-android")
  lazy val extractBuild: TaskKey[BuildData] = TaskKey[BuildData]("ss-extract-build")
  lazy val extractDependencies: TaskKey[DependencyData] = TaskKey[DependencyData]("ss-extract-dependencies")
  lazy val extractProject: TaskKey[Seq[ProjectData]] = TaskKey[Seq[ProjectData]]("ss-extract-project")
  lazy val extractProjects: TaskKey[Seq[ProjectData]] = TaskKey[Seq[ProjectData]]("ss-extract-projects")
  lazy val extractRepository: TaskKey[Option[RepositoryData]] = TaskKey[Option[RepositoryData]]("ss-extract-repository")

  lazy val sbtStructureOptions: SettingKey[String] = SettingKey[String]("sbt-structure-options")
  lazy val sbtStructureOutputFile: SettingKey[Option[File]] = SettingKey[Option[File]]("sbt-structure-output-file")
  lazy val extractStructure: TaskKey[StructureData] = TaskKey[StructureData]("extract-structure")
  lazy val dumpStructure: TaskKey[Unit] = TaskKey[Unit]("dump-structure")
  lazy val dumpStructureTo: InputKey[File] = InputKey[File]("dump-structure-to")
}
