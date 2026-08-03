package org.jetbrains.sbt

import org.jetbrains.sbt.dump.workflow.DumpTaskInstaller
import sbt._

/** Stable side-loading entry point for the structure-dump task installer. */
object CreateTasks extends (State => State) with SbtStateOps {
  lazy val globalSettings: Seq[Setting[_]] = DumpTaskInstaller.globalSettings
  lazy val projectSettings: Seq[Setting[_]] = DumpTaskInstaller.projectSettings

  def apply(state: State): State =
    DumpTaskInstaller(state)
}
