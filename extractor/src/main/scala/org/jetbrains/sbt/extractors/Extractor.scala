package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.ModuleIdentifier
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
trait Extractor {
  type Data
  def extract(implicit state: State, options: Extractor.Options): Option[Data]

  def structure(implicit state: State): Load.BuildStructure =
    Project.extract(state).structure

  def setting[T](key: SettingKey[T])(implicit state: State): Option[T] =
    key.get(structure.data)

  def projectSetting[T](key: SettingKey[T])(implicit state: State, projectRef: ProjectRef): Option[T] =
    key.in(projectRef).get(structure.data)

  def task[T](key: TaskKey[T])(implicit state: State): Option[T] =
    Project.runTask(key, state).collect { case (_, Value(value)) => value }

  def projectTask[T](key: TaskKey[T])(implicit state: State, projectRef: ProjectRef): Option[T] =
    Project.runTask(key.in(projectRef), state).collect { case (_, Value(value)) => value }
}

trait ModulesExtractor extends Extractor {

  protected val DependencyConfigurations = Seq(Compile, Test, Runtime, Provided, Optional, IntegrationTest)

  protected def fuseClassifier(artifact: Artifact): String = {
    val fusingClassifiers = Seq("", Artifact.DocClassifier, Artifact.SourceClassifier)
    artifact.classifier match {
      case Some(c) if fusingClassifiers.contains(c) => fusingClassifiers.head
      case Some(c) => c
      case None => fusingClassifiers.head
    }
  }

  protected def createModuleIdentifiers(moduleId: ModuleID, artifacts: Seq[Artifact]): Seq[ModuleIdentifier] =
    artifacts.map(fuseClassifier).distinct.map { classifier =>
      ModuleIdentifier(moduleId.organization, moduleId.name, moduleId.revision, Artifact.DefaultType, classifier)
    }
}

object Extractor {
  final case class Options(download: Boolean, resolveClassifiers: Boolean, resolveSbtClassifiers: Boolean, cachedUpdate: Boolean)
}
