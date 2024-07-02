package org.jetbrains.sbt

import sbt.State
import sbt.jetbrains.LogDownloadArtifacts

object StateTransformer extends (State => State) with SbtStateOps {
  override def apply(state: State): State = {
    val globalSettings = CreateTasks.globalSettings ++ LogDownloadArtifacts.globalSettings
    val projectSettings = CreateTasks.projectSettings ++ LogDownloadArtifacts.projectSettings
    applySettings(state, globalSettings, projectSettings)
  }
}
