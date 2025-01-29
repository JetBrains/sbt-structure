package org.jetbrains.sbt.extractors

import java.io.File

import org.jetbrains.sbt.{LoadedBuildUnitAdapter, SbtStateOps, StructureKeys, TaskOps, UpdateReportAdapter}
import org.jetbrains.sbt.structure.BuildData
import sbt._
import sbt.jetbrains.PluginCompat._
import scala.collection.Seq

class BuildExtractor(unit: LoadedBuildUnitAdapter, updateSbtClassifiers: Option[UpdateReportAdapter]) {
  private[extractors] def extract: BuildData = {
    val (docs, sources) = extractSbtClassifiers
    BuildData(unit.uri, unit.imports, unit.pluginsClasspath.map(_.data), docs, sources)
  }

  private def extractSbtClassifiers: (Seq[File], Seq[File]) =
    updateSbtClassifiers.map { updateReport =>
      val allArtifacts = updateReport.allModules.flatMap(_.artifacts)
      def artifacts(kind: String) = allArtifacts.filter(_._1.`type` == kind).map(_._2).distinct
      (artifacts(Artifact.DocType), artifacts(Artifact.SourceType))
    }.getOrElse((Seq.empty, Seq.empty))
}

object BuildExtractor extends SbtStateOps with TaskOps {
  def taskDef: Def.Initialize[Task[BuildData]] = Def.taskDyn {
    val state = Keys.state.value
    val projectRef = Keys.thisProjectRef.value
    val options = StructureKeys.sbtStructureOpts.value
    val unit = LoadedBuildUnitAdapter(structure(state).units(projectRef.build))

    Def.task {
      (projectRef / Keys.updateSbtClassifiers).get(state)
        .onlyIf(options.download && options.resolveSbtClassifiers)
        .map { updateClassifiersOpt =>
          new BuildExtractor(unit, updateClassifiersOpt.map(new UpdateReportAdapter(_))).extract
        }.value
    }
  }
}
