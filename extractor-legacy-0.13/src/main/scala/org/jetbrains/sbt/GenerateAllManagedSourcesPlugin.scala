package org.jetbrains.sbt

import sbt.Keys.managedSources
//noinspection scala2InSource3
import sbt._

/**
 * An sbt plugin developed for the purposes of generating all `managedSources` in a given sbt project.
 * The plugin is implemented as a global task which invokes the `managedSources` task in the Compile and Test
 * configurations of all subprojects within the sbt project and aggregates the generated source files.
 */
//The class is used indirectly by Scala Plugin
//noinspection ScalaUnusedSymbol
object GenerateAllManagedSourcesPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val ideaGenerateAllManagedSources = taskKey[Seq[File]]("Generate managed sources in all subprojects")
  }

  import autoImport.ideaGenerateAllManagedSources

  private val scopeFilter = ScopeFilter(inAnyProject, inConfigurations(Compile, Test))

  //noinspection scala2InSource3
  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    ideaGenerateAllManagedSources := { managedSources.all(scopeFilter).value.flatten }
  )
}
