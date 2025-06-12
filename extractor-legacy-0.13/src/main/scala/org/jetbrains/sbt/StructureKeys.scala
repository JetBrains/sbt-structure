package org.jetbrains.sbt

import org.jetbrains.sbt.structure._
import sbt.KeyRanks._
import sbt.{Configuration, _}

object StructureKeys {
  val dependencyConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssDependencyConfigurations", rank = Invisible)
  val sourceConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssSourceConfigurations", rank = Invisible)
  val testConfigurations: SettingKey[Seq[Configuration]] = SettingKey("ssTestConfigurations", rank = Invisible)
  val acceptedProjects: TaskKey[Seq[ProjectRef]] = TaskKey("ssAcceptedProjects", rank = Invisible)

  val extractPlay2: TaskKey[Option[Play2Data]] = TaskKey("ssExtractPlay2", rank = Invisible)
  val extractBuild: TaskKey[BuildData] = TaskKey("ssExtractBuild", rank = Invisible)
  val extractBuilds: TaskKey[Seq[BuildData]] = TaskKey("ssExtractBuilds", rank = Invisible)
  val extractDependencies: TaskKey[DependencyData] = TaskKey("ssExtractDependencies", rank = Invisible)
  val extractProject: TaskKey[ProjectData] = TaskKey("ssExtractProject", rank = Invisible)
  val extractProjects: TaskKey[Seq[ProjectData]] = TaskKey("ssExtractProjects", rank = Invisible)
  val extractRepository: TaskKey[Option[RepositoryData]] = TaskKey("ssExtractRepository", rank = Invisible)
  val extractStructure: TaskKey[StructureData] = TaskKey("extractStructure", rank = Invisible)

  val settingData: TaskKey[Seq[SettingData]] = TaskKey("settingData", rank = Invisible)
  val taskData: TaskKey[Seq[TaskData]] = TaskKey("taskData", rank = Invisible)
  val commandData: TaskKey[Seq[CommandData]] = TaskKey("commandData", rank = Invisible)
  val localCachePath: TaskKey[Option[File]] = TaskKey("localCachePath", rank = Invisible)
  val allKeys:TaskKey[Seq[AttributeKey[_]]] = TaskKey("allKeys", rank = Invisible)
  val allConfigurationsWithSource: SettingKey[Seq[Configuration]] = SettingKey("allConfigurationsWithSource", rank = Invisible)

  val sbtStructureOpts: SettingKey[Options] = SettingKey("ssOptions", "options for dumpStructure task", rank = DSetting)
  val sbtStructureOptions: SettingKey[String] = SettingKey("sbtStructureOptions", "options for dumpStructure task as string", rank = DSetting)
  val sbtStructureOutputFile: SettingKey[Option[File]] = SettingKey("sbtStructureOutputFile", "output file for dumpStructure task",rank = DSetting)
  val generateManagedSourcesDuringStructureDump: SettingKey[Boolean] = SettingKey("generateManagedSourcesDuringStructureDump", "generate managed sources during dumpStructure task", rank = DSetting)

  val loadOptions: TaskKey[Options] = TaskKey("ssLoadOptions", "load options from ssOptionsFile if available", rank = Invisible)
  val dumpStructure: TaskKey[Unit] = TaskKey("dumpStructure", "dump project structure to XML readable by IntelliJ", rank = DTask)
  val dumpStructureTo: InputKey[File] = InputKey("dumpStructureTo", "dump structure to specified file using provided command line options", rank = DTask)
}
