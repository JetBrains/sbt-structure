package sbt.jetbrains

import org.jetbrains.sbt.SbtStateOps
import sbt._
import sbt.Keys.csrLogger
import sbt.jetbrains.apiAdapter._

object LogDownloadArtifacts extends (State => State) with SbtStateOps {

  lazy val globalSettings: Seq[Setting[_]] = Seq[Setting[_]](
    csrLogger := keysAdapterEx.artifactDownloadCsrLogger.value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq[Setting[_]](
  )

  def apply(state: State): State =
    applySettings(state, globalSettings, projectSettings)

  private def applySettings(state: State, globalSettings: Seq[Setting[_]], projectSettings: Seq[Setting[_]]): State = {
    val extracted = Project.extract(state)
    import extracted.{structure => extractedStructure, _}
    val transformedGlobalSettings = Project.transform(_ => GlobalScope, globalSettings)
    val transformedProjectSettings = extractedStructure.allProjectRefs.flatMap { projectRef =>
      transformSettings(projectScope(projectRef), projectRef.build, rootProject, projectSettings)
    }
    reapply(extracted.session.appendRaw(transformedGlobalSettings ++ transformedProjectSettings), state)
  }

  // copied from sbt.internal.Load
  private def transformSettings(thisScope: Scope, uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
    Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings)

  // copied from sbt.internal.SessionSettings
  private def reapply(session: SessionSettings, s: State): State =
    BuiltinCommands.reapply(session, Project.structure(s), s)
}
