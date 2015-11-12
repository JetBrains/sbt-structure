package org.jetbrains.sbt

import org.jetbrains.sbt.structure.ModuleIdentifier
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
trait SbtStateOps {
  def structure(state: State): Load.BuildStructure =
    sbt.Project.structure(state)

  def setting[T](key: SettingKey[T], state: State): Option[T] =
    key.get(structure(state).data)

  def projectSetting[T](key: SettingKey[T])(implicit state: State, projectRef: ProjectRef): Option[T] =
    key.in(projectRef).get(structure(state).data)

  def task[T](key: TaskKey[T])(implicit state: State): Option[T] =
    Project.runTask(key, state).collect { case (_, Value(value)) => value }

  def projectTask[T](key: TaskKey[T])(implicit state: State, projectRef: ProjectRef): Option[T] =
    Project.runTask(key.in(projectRef), state).collect { case (_, Value(value)) => value }
}

trait ModulesOps {

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

