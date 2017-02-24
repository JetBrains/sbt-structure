package org.jetbrains.sbt

import org.jetbrains.sbt.extractors._
import sbt._

/**
 * @author Nikolay Obedin
 */

object CreateTasks extends (State => State) with SbtStateOps {

  def globalSettings: Seq[Setting[_]] = Seq[Setting[_]](
    StructureKeys.sbtStructureOpts := StructureKeys.sbtStructureOptions.apply(Options.readFromString).value,
    StructureKeys.dumpStructure := UtilityTasks.dumpStructure.value
  )

  def projectSettings: Seq[Setting[_]] = Seq[Setting[_]](
    Keys.classifiersModule.in(Keys.updateClassifiers) :=
      UtilityTasks.classifiersModuleRespectingStructureOpts.value,
    StructureKeys.dependencyConfigurations := UtilityTasks.dependencyConfigurations.value,
    StructureKeys.extractProject := ProjectExtractor.taskDef.value
  )


  def apply(state: State): State =
    applySettings(state, globalSettings, projectSettings)

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
