package org.jetbrains.sbt

import org.jetbrains.sbt.extractors._
import sbt._
import sbt.jetbrains.apiAdapter._

/**
 * @author Nikolay Obedin
 */

object CreateTasks extends (State => State) with SbtStateOps {

  lazy val globalSettings: Seq[Setting[_]] = Seq[Setting[_]](
    Keys.commands += UtilityTasks.preferScala2,
    StructureKeys.sbtStructureOpts := StructureKeys.sbtStructureOptions.apply(Options.readFromString).value,
    StructureKeys.dumpStructure := UtilityTasks.dumpStructure.value,
    StructureKeys.acceptedProjects := UtilityTasks.acceptedProjects.value,
    StructureKeys.extractProjects := UtilityTasks.extractProjects.value,
    StructureKeys.extractBuilds := UtilityTasks.extractBuilds.value,
    StructureKeys.extractRepository := RepositoryExtractor.taskDef.value,
    StructureKeys.extractStructure := extractors.extractStructure.value,
    StructureKeys.localCachePath := UtilityTasks.localCachePath.value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq[Setting[_]](
    Keys.classifiersModule.in(Keys.updateClassifiers) := UtilityTasks.classifiersModuleRespectingStructureOpts.value,
    StructureKeys.dependencyConfigurations := UtilityTasks.dependencyConfigurations.value,
    StructureKeys.testConfigurations := UtilityTasks.testConfigurations.value,
    StructureKeys.sourceConfigurations := UtilityTasks.sourceConfigurations.value,

    StructureKeys.extractPlay2 := Play2Extractor.taskDef.value,
    StructureKeys.extractBuild := BuildExtractor.taskDef.value,
    StructureKeys.extractDependencies := DependenciesExtractor.taskDef.value,
    StructureKeys.extractProject := ProjectExtractor.taskDef.value,

    StructureKeys.allKeys := KeysExtractor.allKeys.value,
    StructureKeys.taskData := KeysExtractor.taskData.value,
    StructureKeys.settingData := KeysExtractor.settingData.value,
    StructureKeys.commandData := KeysExtractor.commandData.value,

    StructureKeys.allConfigurationsWithSource := UtilityTasks.allConfigurationsWithSource.value
  )



  def apply(state: State): State =
    applySettings(state, globalSettings, projectSettings)

}
