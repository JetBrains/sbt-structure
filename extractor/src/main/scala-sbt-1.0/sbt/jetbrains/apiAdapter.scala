package sbt.jetbrains

import org.jetbrains.sbt.structure.ResolverData
import sbt.{ConfigurationReport, MavenRepository, Reference, Scope, Select, Zero}
import sbt.librarymanagement.GetClassifiersModule

/**
  * Hacky workaround for some types having been moved to internal in sbt 1.0.
  * Alias these types differently for 0.13 and 1.0 builds. Import this object to shadow the regular sbt definitions.
  */
object apiAdapter {
  type Load = sbt.internal.Load.type
  type SessionSettings = sbt.internal.SessionSettings
  type GetClassifiersModule = sbt.librarymanagement.GetClassifiersModule
  type LoadedBuildUnit = sbt.internal.LoadedBuildUnit
  type BuildStructure = sbt.internal.BuildStructure
  type BuildDependencies = sbt.internal.BuildDependencies
  type ScalaInstance = sbt.internal.inc.ScalaInstance
  type MavenRepository = sbt.librarymanagement.MavenRepository


  // copied from sbt.internal.Load
  def projectScope(project: Reference): Scope = Scope(Select(project), Zero, Zero, Zero)

  def configReportName(configReport: ConfigurationReport): String = configReport.configuration.name
}
