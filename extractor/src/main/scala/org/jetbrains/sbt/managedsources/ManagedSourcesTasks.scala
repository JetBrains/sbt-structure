package org.jetbrains.sbt.managedsources

import sbt.Keys.managedSources
//noinspection scala2InSource3
import sbt._

/** Task definitions used by the managed-sources plugin. */
object ManagedSourcesTasks {
  private val scopeFilter = ScopeFilter(inAnyProject, inConfigurations(Compile, Test))

  val generateAll: Def.Initialize[Task[Seq[File]]] = Def.task {
    managedSources.all(scopeFilter).value.flatten
  }
}
