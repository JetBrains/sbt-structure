package org.jetbrains.sbt
package extractors

import java.io.File

import org.jetbrains.sbt.structure.BuildData
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class BuildExtractor(unit: sbt.Load.LoadedBuildUnit, updateSbtClassifiers: Option[UpdateReport]) {
  private def extract: BuildData = {
    val (docs, sources) = extractSbtClassifiers
    BuildData(unit.imports, unit.unit.plugins.pluginData.dependencyClasspath.map(_.data), docs, sources)
  }

  private def extractSbtClassifiers: (Seq[File], Seq[File]) =
    updateSbtClassifiers.map { updateReport =>
      val allArtifacts = updateReport.configurations.flatMap(_.modules.flatMap(_.artifacts))
      def artifacts(kind: String) = allArtifacts.filter(_._1.`type` == kind).map(_._2).distinct
      (artifacts(Artifact.DocType), artifacts(Artifact.SourceType))
    }.getOrElse((Seq.empty, Seq.empty))
}

object BuildExtractor extends Extractor {
  def apply(implicit state: State, projectRef: ProjectRef, options: Options): BuildData = {
    val unit = structure.units(projectRef.build)
    val updateSbtClassifiers =
      if (options.download && options.resolveSbtClassifiers)
        projectTask(Keys.updateSbtClassifiers)
      else
        None
    new BuildExtractor(unit, updateSbtClassifiers).extract
  }
}