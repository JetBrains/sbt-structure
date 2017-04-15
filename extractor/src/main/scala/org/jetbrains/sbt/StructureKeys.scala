package org.jetbrains.sbt

import sbt.{Configuration, _}
import structure._
import KeyRanks._

/**
  * @author Nikolay Obedin
  * @since 11/10/15.
  */
object StructureKeys {
  val dependencyConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ss-dependency-configurations", rank = Invisible)
  val sourceConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ss-source-configurations", rank = Invisible)
  val testConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ss-test-configurations", rank = Invisible)
  val acceptedProjects: TaskKey[Seq[ProjectRef]] = TaskKey("ss-accepted-projects", rank = Invisible)

  val extractPlay2: TaskKey[Option[Play2Data]] = TaskKey("ss-extract-play-2", rank = Invisible)
  val extractAndroid: TaskKey[Option[AndroidData]] = TaskKey("ss-extract-android", rank = Invisible)
  val extractBuild: TaskKey[BuildData] = TaskKey("ss-extract-build", rank = Invisible)
  val extractDependencies: TaskKey[DependencyData] = TaskKey("ss-extract-dependencies", rank = Invisible)
  val extractProject: TaskKey[Seq[ProjectData]] = TaskKey("ss-extract-project", rank = Invisible)
  val extractProjects: TaskKey[Seq[ProjectData]] = TaskKey("ss-extract-projects", rank = Invisible)
  val extractRepository: TaskKey[Option[RepositoryData]] = TaskKey("ss-extract-repository", rank = Invisible)
  val extractStructure: TaskKey[StructureData] = TaskKey("extract-structure", rank = Invisible)

  val settingData: TaskKey[Seq[SettingData]] = TaskKey("settingData", rank = Invisible)
  val taskData: TaskKey[Seq[TaskData]] = TaskKey("taskData", rank = Invisible)
  val commandData: TaskKey[Seq[CommandData]] = TaskKey("commandData", rank = Invisible)
  val localCachePath: TaskKey[Option[File]] = TaskKey("localCachePath", rank = Invisible)
  val allKeys:TaskKey[Seq[AttributeKey[_]]] = TaskKey("allKeys", rank = Invisible)
  val allConfigurationsWithSource: SettingKey[Seq[Configuration]] = SettingKey("allConfigurationsWithSource", rank = Invisible)

  val sbtStructureOpts: SettingKey[Options] = SettingKey("ss-options", rank = DSetting)
  val sbtStructureOptions: SettingKey[String] = SettingKey("sbt-structure-options", "options for dumpStructure task", rank = DSetting)
  val sbtStructureOutputFile: SettingKey[Option[File]] = SettingKey("sbt-structure-output-file", "output file for dumpStructure task",rank = DSetting)
  val dumpStructure: TaskKey[Unit] = TaskKey("dump-structure", "dump project structure to XML readable by IntelliJ", rank = DTask)
  val dumpStructureTo: InputKey[File] = InputKey("dump-structure-to", "dump structure to specified file using provided command line options", rank = DTask)
}
