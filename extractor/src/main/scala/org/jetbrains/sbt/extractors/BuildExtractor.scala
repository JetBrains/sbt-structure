package org.jetbrains.sbt
package extractors

import java.io.File

import org.jetbrains.sbt.structure.BuildData
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class BuildExtractor(implicit projectRef: ProjectRef) extends Extractor {
  def extract(implicit state: State, options: Options): Option[BuildData] = {
    val unit = structure.units(projectRef.build)
    val (docs, sources) = if (options.download && options.resolveSbtClassifiers) extractSbtClassifiers else (Seq.empty, Seq.empty)
    Some(BuildData(unit.imports, unit.unit.plugins.pluginData.dependencyClasspath.map(_.data), docs, sources))
  }

  private def extractSbtClassifiers(implicit state: State): (Seq[File], Seq[File]) = {
    val updateReport = projectTask(Keys.updateSbtClassifiers).getOrElse(throw new RuntimeException())
    val allArtifacts = updateReport.configurations.flatMap(_.modules.flatMap(_.artifacts))

    def artifacts(kind: String) = allArtifacts.filter(_._1.`type` == kind).map(_._2).distinct

    (artifacts(Artifact.DocType), artifacts(Artifact.SourceType))
  }
}

object BuildExtractor {
  def apply(implicit state: State, projectRef: ProjectRef, options: Options): Option[BuildData] =
    new BuildExtractor().extract
}