package org.jetbrains.sbt

import org.jetbrains.sbt.extractors._
import sbt._

/**
 * @author Nikolay Obedin
 */

object CreateTasks extends (State => State) {
  def apply(state: State) = {
    val globalSettings = Seq[Setting[_]](
      StructureKeys.sbtStructureOpts <<=
        StructureKeys.sbtStructureOptions.apply(Options.readFromString),
      StructureKeys.dumpStructure <<=
        UtilityTasks.dumpStructure,

      StructureKeys.acceptedProjects <<=
        UtilityTasks.acceptedProjects,

      StructureKeys.extractProjects <<=
        (Keys.state, StructureKeys.sbtStructureOpts, StructureKeys.acceptedProjects).map {
          (state, options, acceptedProjects) =>
            acceptedProjects.flatMap(ref => ProjectExtractor(ref)(state, options))
        },
      StructureKeys.extractRepository <<=
        (Keys.state, StructureKeys.sbtStructureOpts, StructureKeys.acceptedProjects).map {
          (state, options, acceptedProjects) =>
            RepositoryExtractor(acceptedProjects)(state, options)
        },
      StructureKeys.extractStructure <<=
        StructureExtractor.task
    )

    val projectSettings = Seq[Setting[_]](
      StructureKeys.testConfigurations <<=
        UtilityTasks.testConfigurations,
      StructureKeys.sourceConfigurations <<=
        UtilityTasks.sourceConfigurations,
      StructureKeys.dependencyConfigurations <<=
        UtilityTasks.dependencyConfigurations
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
    val newStructure = Load.reapply(session.mergeSettings ++ transformedGlobalSettings ++ transformedProjectSettings, extractedStructure)
    Project.setProject(session, newStructure, state)
  }
}
