package org.jetbrains.sbt

import org.jetbrains.sbt.dump.workflow.DumpTaskInstaller
import sbt.*

/** Stable side-loading entry point for the structure-dump task installer. */
object CreateTasks extends (State => State) with SbtStateOps {
  lazy val globalSettings: Seq[Setting[?]] = DumpTaskInstaller.globalSettings
  lazy val projectSettings: Seq[Setting[?]] = DumpTaskInstaller.projectSettings

  def apply(state: State): State =
    DumpTaskInstaller(state)
}
