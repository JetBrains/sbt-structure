package org.jetbrains.sbt

import org.jetbrains.sbt.structure.ModuleIdentifier
import sbt.*
import sbt.internal.{BuildStructure, SessionSettings}
import sbt.jetbrains.PluginCompat.*

import scala.collection.Seq

trait SbtStateOps {

  def applySettings(state: State, globalSettings: Seq[Setting[_]], projectSettings: Seq[Setting[_]]): State = {
    val extracted = Project.extract(state)
    import extracted.{structure as extractedStructure, *}
    val transformedGlobalSettings = Project.transform(_ => GlobalScope, globalSettings.toSbtSeqType)
    val transformedProjectSettings = extractedStructure.allProjectRefs.flatMap { projectRef =>
      transformSettings(projectScope(projectRef), projectRef.build, rootProject, projectSettings)
    }
    reapply(extracted.session.appendRaw(transformedGlobalSettings ++ transformedProjectSettings), state)
  }

  // copied from sbt.internal.Load
  private def transformSettings(thisScope: Scope, uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
    Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings.toSbtSeqType)

  // copied from sbt.internal.SessionSettings
  private def reapply(session: SessionSettings, s: State): State =
    BuiltinCommands.reapply(session, Project.structure(s), s)

  def structure(state: State): BuildStructure =
    sbt.Project.structure(state)

  implicit final class SettingKeyOps[T](key: SettingKey[T]) {
    def find(state: State): Option[T] =
      key.get(structure(state).data)

    def get(state: State): T =
      find(state).get

    def getValueOrElse(state: State, default: => T): T =
      find(state).getOrElse(default)

    def forAllProjects(state: State, projects: Seq[ProjectRef]): Seq[(ProjectRef, T)] =
      projects.flatMap(p => (p / key).find(state).map(it => (p, it)))

    def forAllConfigurations(state: State, configurations: Seq[sbt.Configuration]): Seq[(sbt.Configuration, T)] =
      configurations.flatMap(c => (c / key).get(structure(state).data).map(it => (c, it)))
  }

  implicit final class TaskKeyOps[T](key: TaskKey[T]) {
    def find(state: State): Option[Task[T]] =
      key.get(structure(state).data)

    def get(state: State): Task[T] =
      find(state).get

    def getOrElse(state: State, default: => Task[T]): Task[T] =
      find(state).getOrElse(default)

    def forAllProjects(state: State, projects: Seq[ProjectRef]): Task[Map[ProjectRef, T]] = {
      val tasks = projects.flatMap(p => (p / key).get(structure(state).data).map(_.map(it => (p, it))))
      std.TaskExtra.joinTasks(tasks.toSbtSeqType).join.map(_.toMap)
    }

    def forAllConfigurations(state: State, configurations: Seq[sbt.Configuration]): Task[Map[sbt.Configuration, T]] = {
      val tasks = configurations.flatMap(c => (c / key).get(structure(state).data).map(_.map(it => (c, it))))
      std.TaskExtra.joinTasks(tasks.toSbtSeqType).join.map(_.toMap)
    }

    def forAllProjectsAndConfigurations(state: State, projects: Seq[ProjectRef], configurations: Map[ProjectRef, Seq[sbt.Configuration]]): Task[Seq[((ProjectRef, sbt.Configuration), T)]] = {
      val tasks = for {
        project <- projects
        config <- configurations.getOrElse(project, Seq.empty)
        task <- (project / config / key).get(structure(state).data).map(_.map(it => ((project, config), it)))
      } yield task

      std.TaskExtra.joinTasks(tasks.toSbtSeqType).join
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

