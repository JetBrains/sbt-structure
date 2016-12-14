package org.jetbrains.sbt

import org.jetbrains.sbt.extractors._
import sbt._

/**
 * @author Nikolay Obedin
 */

object CreateTasks extends (State => State) with SbtStateOps {
  def apply(state: State): State = {
    val globalSettings = Seq[Setting[_]](
      StructureKeys.sbtStructureOpts :=
        StructureKeys.sbtStructureOptions.apply(Options.readFromString).value,
      StructureKeys.dumpStructure :=
        UtilityTasks.dumpStructure.value,
      StructureKeys.acceptedProjects :=
        UtilityTasks.acceptedProjects.value,
      StructureKeys.extractProjects := Def.taskDyn {
        val state = Keys.state.value
        val accepted = StructureKeys.acceptedProjects.value

        Def.task {
          StructureKeys.extractProject
            .forAllProjects(state, accepted)
            .map(_.values.toSeq.flatten)
            .value
        }
      }.value,
      StructureKeys.extractRepository :=
        RepositoryExtractor.taskDef.value,
      StructureKeys.extractStructure :=
        StructureExtractor.taskDef.value
    )

    val projectSettings = Seq[Setting[_]](
      StructureKeys.testConfigurations :=
        UtilityTasks.testConfigurations.value,
      StructureKeys.sourceConfigurations :=
        UtilityTasks.sourceConfigurations.value,
      StructureKeys.dependencyConfigurations :=
        UtilityTasks.dependencyConfigurations.value,
      StructureKeys.extractAndroid :=
        tasks.extractAndroidSdkPlugin.value,
      StructureKeys.extractPlay2 :=
        Play2Extractor.taskDef.value,
      StructureKeys.extractBuild :=
        BuildExtractor.taskDef.value,
      StructureKeys.extractDependencies :=
        DependenciesExtractor.taskDef.value,
      StructureKeys.extractProject :=
        ProjectExtractor.taskDef.value,
      Keys.classifiersModule.in(Keys.updateClassifiers) :=
        UtilityTasks.classifiersModuleRespectingStructureOpts.value
    )

    applySettings(state, globalSettings, projectSettings)
  }

  private def applySettings(state: State, globalSettings: Seq[Setting[_]], projectSettings: Seq[Setting[_]]): State = {
    val extracted = Project.extract(state)
    import extracted.{structure => extractedStructure, _}
    val transformedGlobalSettings = Project.transform(_ => GlobalScope, globalSettings)
    val transformedProjectSettings = extractedStructure.allProjectRefs.flatMap { projectRef =>
      Load.transformSettings(Load.projectScope(projectRef), projectRef.build, rootProject, projectSettings)
    }
    SessionSettings.reapply(extracted.session.appendRaw(transformedGlobalSettings ++ transformedProjectSettings), state)
  }
}
