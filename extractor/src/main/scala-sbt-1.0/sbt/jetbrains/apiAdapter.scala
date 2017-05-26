package sbt.jetbrains

import org.jetbrains.sbt.structure.ResolverData
import sbt.MavenRepository

/**
  * Hacky workaround for some types having been moved to internal in sbt 1.0.
  * Alias these types differently for 0.13 and 1.0 builds. Import this object to shadow the regular sbt definitions.
  */
object apiAdapter {
  val Load = sbt.internal.Load
  type Load = sbt.internal.Load.type

  val SessionSettings = sbt.internal.SessionSettings
  type SessionSettings = sbt.internal.SessionSettings

  type GetClassifiersModule = sbt.internal.librarymanagement.GetClassifiersModule
  type LoadedBuildUnit = sbt.internal.LoadedBuildUnit
  type BuildStructure = sbt.internal.BuildStructure
  type BuildDependencies = sbt.internal.BuildDependencies
  type ScalaInstance = sbt.internal.inc.ScalaInstance
  type MavenRepository = sbt.librarymanagement.MavenRepository
}
