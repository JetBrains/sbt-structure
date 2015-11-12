package org.jetbrains.sbt
package extractors

import java.io.File

import org.jetbrains.sbt.structure.BuildData
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class BuildExtractor(unit: LoadedBuildUnitAdapter, updateSbtClassifiers: Option[UpdateReportAdapter]) {
  def extract: BuildData = {
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

object BuildExtractor extends SbtStateOps {
  def apply(implicit state: State, projectRef: ProjectRef, options: Options): BuildData = {
    val unit = LoadedBuildUnitAdapter(structure(state).units(projectRef.build))
    val updateSbtClassifiers =
      if (options.download && options.resolveSbtClassifiers)
        projectTask(Keys.updateSbtClassifiers).map(new UpdateReportAdapter(_))
      else
        None
    new BuildExtractor(unit, updateSbtClassifiers).extract
  }
}
