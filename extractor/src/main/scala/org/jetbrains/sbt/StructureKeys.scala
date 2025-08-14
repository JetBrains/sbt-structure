package org.jetbrains.sbt

import org.jetbrains.sbt.structure.*
import sbt.KeyRanks.*
import sbt.util.cacheLevel
import sbt.{Configuration, *}

import scala.collection.Seq

//Note a more idiomatic way to define keys is to use the low-case names so we might consider rewriting it
// https://github.com/sbt/sbt/pull/8016#issuecomment-2612955340
object StructureKeys {
  val dependencyConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssDependencyConfigurations", description = "", rank = Invisible)
  val sourceConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssSourceConfigurations", description = "", rank = Invisible)
  val testConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssTestConfigurations", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val acceptedProjects: TaskKey[Seq[ProjectRef]] = TaskKey("ssAcceptedProjects", description = "", rank = Invisible)

  @cacheLevel(include = Array.empty) val extractPlay2: TaskKey[Option[Play2Data]] = TaskKey("ssExtractPlay2", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val extractBuild: TaskKey[BuildData] = TaskKey("ssExtractBuild", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val extractBuilds: TaskKey[Seq[BuildData]] = TaskKey("ssExtractBuilds", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val extractDependencies: TaskKey[DependencyData] = TaskKey("ssExtractDependencies", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val extractProject: TaskKey[ProjectData] = TaskKey("ssExtractProject", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val extractProjects: TaskKey[Seq[ProjectData]] = TaskKey("ssExtractProjects", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val extractRepository: TaskKey[Option[RepositoryData]] = TaskKey("ssExtractRepository", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val extractStructure: TaskKey[StructureData] = TaskKey("extractStructure", description = "", rank = Invisible)

  @cacheLevel(include = Array.empty) val settingData: TaskKey[Seq[SettingData]] = TaskKey("settingData", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val taskData: TaskKey[Seq[TaskData]] = TaskKey("taskData", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val commandData: TaskKey[Seq[CommandData]] = TaskKey("commandData", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val localCachePath: TaskKey[Option[File]] = TaskKey("localCachePath", description = "", rank = Invisible)
  @cacheLevel(include = Array.empty) val allKeys: TaskKey[Seq[AttributeKey[_]]] = TaskKey("allKeys", description = "", rank = Invisible)
  val allConfigurationsWithSource: SettingKey[Seq[Configuration]] = SettingKey("allConfigurationsWithSource", description = "", rank = Invisible)

  val sbtStructureOpts: SettingKey[Options] = SettingKey("ssOptions", "options for dumpStructure task", rank = DSetting)
  val sbtStructureOptions: SettingKey[String] = SettingKey("sbtStructureOptions", "options for dumpStructure task as string", rank = DSetting)
  val sbtStructureOutputFile: SettingKey[Option[File]] = SettingKey("sbtStructureOutputFile", "output file for dumpStructure task",rank = DSetting)
  val generateManagedSourcesDuringStructureDump: SettingKey[Boolean] = SettingKey("generateManagedSourcesDuringStructureDump", "generate managed sources during dumpStructure task", rank = DSetting)

  val loadOptions: TaskKey[Options] = TaskKey("ssLoadOptions", "load options from ssOptionsFile if available", rank = Invisible)
  @cacheLevel(include = Array.empty) val dumpStructure: TaskKey[Unit] = TaskKey("dumpStructure", "dump project structure to XML readable by IntelliJ", rank = DTask)
  val dumpStructureTo: InputKey[File] = InputKey("dumpStructureTo", "dump structure to specified file using provided command line options", rank = DTask)
}
