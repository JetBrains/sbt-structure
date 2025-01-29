package org.jetbrains.sbt

import org.jetbrains.sbt.structure._
import sbt.KeyRanks._
import sbt.{Configuration, _}

import scala.collection.Seq

//Note a more idiomatic way to define keys is to use the low-case names so we might consider rewriting it
// https://github.com/sbt/sbt/pull/8016#issuecomment-2612955340
object StructureKeys {
  val dependencyConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssDependencyConfigurations", description = "", rank = Invisible)
  val sourceConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssSourceConfigurations", description = "", rank = Invisible)
  val testConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssTestConfigurations", description = "", rank = Invisible)
  val acceptedProjects: TaskKey[Seq[ProjectRef]] = TaskKey("ssAcceptedProjects", description = "", rank = Invisible)

  val extractPlay2: TaskKey[Option[Play2Data]] = TaskKey("ssExtractPlay2", description = "", rank = Invisible)
  val extractBuild: TaskKey[BuildData] = TaskKey("ssExtractBuild", description = "", rank = Invisible)
  val extractBuilds: TaskKey[Seq[BuildData]] = TaskKey("ssExtractBuilds", description = "", rank = Invisible)
  val extractDependencies: TaskKey[DependencyData] = TaskKey("ssExtractDependencies", description = "", rank = Invisible)
  val extractProject: TaskKey[ProjectData] = TaskKey("ssExtractProject", description = "", rank = Invisible)
  val extractProjects: TaskKey[Seq[ProjectData]] = TaskKey("ssExtractProjects", description = "", rank = Invisible)
  val extractRepository: TaskKey[Option[RepositoryData]] = TaskKey("ssExtractRepository", description = "", rank = Invisible)
  val extractStructure: TaskKey[StructureData] = TaskKey("extractStructure", description = "", rank = Invisible)

  val settingData: TaskKey[Seq[SettingData]] = TaskKey("settingData", description = "", rank = Invisible)
  val taskData: TaskKey[Seq[TaskData]] = TaskKey("taskData", description = "", rank = Invisible)
  val commandData: TaskKey[Seq[CommandData]] = TaskKey("commandData", description = "", rank = Invisible)
  val localCachePath: TaskKey[Option[File]] = TaskKey("localCachePath", description = "", rank = Invisible)
  val allKeys: TaskKey[Seq[AttributeKey[_]]] = TaskKey("allKeys", description = "", rank = Invisible)
  val allConfigurationsWithSource: SettingKey[Seq[Configuration]] = SettingKey("allConfigurationsWithSource", description = "", rank = Invisible)

  val sbtStructureOpts: SettingKey[Options] = SettingKey("ssOptions", "options for dumpStructure task", rank = DSetting)
  val sbtStructureOptions: SettingKey[String] = SettingKey("sbtStructureOptions", "options for dumpStructure task as string", rank = DSetting)
  val sbtStructureOutputFile: SettingKey[Option[File]] = SettingKey("sbtStructureOutputFile", "output file for dumpStructure task",rank = DSetting)

  val loadOptions: TaskKey[Options] = TaskKey("ssLoadOptions", "load options from ssOptionsFile if available", rank = Invisible)
  val dumpStructure: TaskKey[Unit] = TaskKey("dumpStructure", "dump project structure to XML readable by IntelliJ", rank = DTask)
  val dumpStructureTo: InputKey[File] = InputKey("dumpStructureTo", "dump structure to specified file using provided command line options", rank = DTask)
}
