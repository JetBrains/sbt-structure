package org.jetbrains.sbt
package extractors

import java.io.File

import org.jetbrains.sbt.structure.BuildData
import sbt.Project.Initialize
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class BuildExtractor(unit: LoadedBuildUnitAdapter, updateSbtClassifiers: Option[UpdateReportAdapter]) {
  private[extractors] def extract: BuildData = {
    val (docs, sources) = extractSbtClassifiers
    BuildData(unit.imports, unit.pluginsClasspath.map(_.data), docs, sources)
  }

  private def extractSbtClassifiers: (Seq[File], Seq[File]) =
    updateSbtClassifiers.map { updateReport =>
      val allArtifacts = updateReport.allModules.flatMap(_.artifacts)
      def artifacts(kind: String) = allArtifacts.filter(_._1.`type` == kind).map(_._2).distinct
      (artifacts(Artifact.DocType), artifacts(Artifact.SourceType))
    }.getOrElse((Seq.empty, Seq.empty))
}

object BuildExtractor extends SbtStateOps with TaskOps {
  def taskDef: Initialize[Task[BuildData]] =
    (sbt.Keys.state, sbt.Keys.thisProjectRef, StructureKeys.sbtStructureOpts) flatMap {
      (state, projectRef, options) =>
        val unit = LoadedBuildUnitAdapter(structure(state).units(projectRef.build))
        Keys.updateSbtClassifiers.in(projectRef).get(state)
          .onlyIf(options.download && options.resolveSbtClassifiers)
          .map { updateClassifiersOpt =>
            new BuildExtractor(unit, updateClassifiersOpt.map(new UpdateReportAdapter(_))).extract
          }
      }
}
