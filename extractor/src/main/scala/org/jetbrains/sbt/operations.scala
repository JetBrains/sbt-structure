package org.jetbrains.sbt

import org.jetbrains.sbt.structure.ModuleIdentifier
import sbt._
import sbt.jetbrains.apiAdapter._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
trait SbtStateOps {
  def structure(state: State): BuildStructure =
    sbt.Project.structure(state)

  implicit class `enrich SettingKey`[T](key: SettingKey[T]) {
    def find(state: State): Option[T] =
      key.get(structure(state).data)

    def get(state: State): T =
      find(state).get

    def getOrElse(state: State, default: => T): T =
      find(state).getOrElse(default)
  }

  implicit class `enrich TaskKey`[T](key: TaskKey[T]) {
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

    // this workaround is created for android projects. For some unknown reason, triggering compilation during
    // internalDependencyClasspath task causes a lint error
    private def substituteDummyConfigNeeded(configuration: sbt.Configuration, isAndroid: Boolean): sbt.Configuration =
      if (isAndroid) {
        val dummyConfiguration = config("dummyConfig").extend(configuration)
        sbt.Keys.trackInternalDependencies.in(dummyConfiguration) := TrackLevel.NoTracking
        dummyConfiguration
      } else configuration

    def forAllConfigurations(state: State, configurations: Seq[sbt.Configuration], isAndroid: Boolean = false): Task[Map[sbt.Configuration, T]] = {
      val tasks = configurations.flatMap { c =>
        val config = substituteDummyConfigNeeded(c, isAndroid)
        key.in(config).get(structure(state).data).map(_.map(it => (c, it)))
      }
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }

    def forAllProjectsAndConfigurations(state: State, configurations: Seq[sbt.Configuration], projects: Seq[ProjectRef], isAndroid: Boolean = false): Task[Map[T, ProjectRef]] = {
      val projectsMergedWithConfigurations = projects.flatMap { p => configurations.map((p, _)) }
      val tasks = projectsMergedWithConfigurations.flatMap { case (project, configuration) =>
        val config = substituteDummyConfigNeeded(configuration, isAndroid)
        key.in(project, config).get(structure(state).data).map(_.map(it => (it, project)))
      }
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }
  }
}

trait TaskOps {
  implicit class `enrich Task`[T](task: Task[T]) {
    def onlyIf(condition: => Boolean): Task[Option[T]] =
      if (condition) task.map(Some(_)) else std.TaskExtra.task(None)
  }

  implicit class `any to Task`[T](value: T) {
    def toTask: Task[T] = std.TaskExtra.task(value)
  }
}

trait ModulesOps {
  def fuseClassifier(artifact: Artifact): String = {
    val fusingClassifiers = Seq("", Artifact.DocClassifier, Artifact.SourceClassifier)
    artifact.classifier match {
      case Some(c) if fusingClassifiers.contains(c) => fusingClassifiers.head
      case Some(c) => c
      case None => fusingClassifiers.head
    }
  }

  def createModuleIdentifiers(moduleId: ModuleID, artifacts: Seq[Artifact]): Seq[ModuleIdentifier] =
    artifacts.map(fuseClassifier).distinct.map { classifier =>
      ModuleIdentifier(moduleId.organization, moduleId.name, moduleId.revision, Artifact.DefaultType, classifier)
    }
}

