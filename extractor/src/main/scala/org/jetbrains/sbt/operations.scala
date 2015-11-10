package org.jetbrains.sbt

import org.jetbrains.sbt.structure.ModuleIdentifier
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
trait SbtStateOps {
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

trait ConfigurationOps {
  protected val PredefinedTestConfigurations = Set(Test, IntegrationTest)

  protected def getTestConfigurations(implicit state: State, projectRef: ProjectRef): Seq[Configuration] = {
    val configurations = Keys.ivyConfigurations.in(projectRef)
      .get(Project.extract(state).structure.data)
      .getOrElse(Seq.empty)
    for {
      configuration <- configurations
      if !configuration.name.toLowerCase.contains("internal")
      if PredefinedTestConfigurations(configuration) || PredefinedTestConfigurations.intersect(configuration.extendsConfigs.toSet).nonEmpty
    } yield configuration
  }

  protected def getDependencyConfigurations(implicit state: State, projectRef: ProjectRef): Seq[Configuration] =
    Seq(Compile, Runtime, Provided, Optional) ++ getTestConfigurations

  protected def getSourceConfigurations(implicit state: State, projectRef: ProjectRef): Seq[Configuration] =
    Seq(Compile) ++ getTestConfigurations
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

