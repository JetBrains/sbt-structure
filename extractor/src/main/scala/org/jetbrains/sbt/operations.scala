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

  implicit def `enrich SettingKey`[T](key: SettingKey[T]) = new {
    def find(state: State): Option[T] =
      key.get(structure(state).data)

    def get(state: State): T =
      find(state).get

    def getOrElse(state: State, default: => T): T =
      find(state).getOrElse(default)
  }

  implicit def `enrich TaskKey`[T](key: TaskKey[T]) = new {
    def find(state: State): Option[Task[T]] =
      key.get(structure(state).data)

    def get(state: State): Task[T] =
      find(state).get

    def getOrElse(state: State, default: => Task[T]): Task[T] =
      find(state).getOrElse(default)

    def forAllProjects(state: State, projects: Seq[ProjectRef]): Task[Map[ProjectRef, T]] = {
      val tasks = projects.flatMap(p => key.in(p).get(structure(state).data).map(_.map(it => (p, it))))
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }
  }

  def projectSetting[T](key: SettingKey[T])(implicit state: State, projectRef: ProjectRef): Option[T] =
    key.in(projectRef).get(structure(state).data)

  def projectTask[T](key: TaskKey[T])(implicit state: State, projectRef: ProjectRef): Option[T] =
    Project.runTask(key.in(projectRef), state).collect { case (_, Value(value)) => value }
}

trait TaskOps {
  implicit def `enrich Task`[T](task: Task[T]) = new {
    def onlyIf(condition: => Boolean): Task[Option[T]] =
      if (condition) task.map(Some(_)) else std.TaskExtra.task(None)
  }

  implicit def `any to Task`[T](value: T) = new {
    def toTask: Task[T] = std.TaskExtra.task(value)
  }
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

