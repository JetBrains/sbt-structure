package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.structure.{ModuleData, ModuleIdentifier, RepositoryData}
import org.jetbrains.sbt.{ModuleReportAdapter, ModulesOps, Options, SbtStateOps, StructureKeys, TaskOps, UpdateReportAdapter}
import sbt.Def.Initialize
import sbt.{Def, _}
import sbt.jetbrains.PluginCompat._

import scala.collection.mutable
import scala.collection.Seq

class RepositoryExtractor(
  projects: Seq[ProjectRef],
  updateReports: ProjectRef => UpdateReportAdapter,
  updateClassifiersReports: Option[ProjectRef => UpdateReportAdapter],
  classpathTypes: ProjectRef => Set[String],
  projectToConfigurationsName: Map[ProjectRef, Seq[String]]
) extends ModulesOps {

  private[extractors] def extract: RepositoryData = {
    val moduleReports = fixModulesIdsToSupportClassifiers(allModulesWithDocs)
    val modulesReportsByIdentifier = groupByModuleIdentifiers(moduleReports)
    val modulesData = modulesReportsByIdentifier.toSeq.map { case (a, b) => createModuleData(a, b) }
    RepositoryData(modulesData)
  }

  private def allModulesWithDocs: Seq[ModuleReportAdapter] = projects.flatMap(moduleWithDoc)

  private def moduleWithDoc(projectRef: ProjectRef): Seq[ModuleReportAdapter] = {

    val modulesWithoutDocs = getModulesForProject(projectRef, updateReports)

    val modulesWithDocs = updateClassifiersReports.map { updateClassifiersReportsFn =>
      def onlySourcesAndDocs(artifacts: Seq[(Artifact, File)]): Seq[(Artifact, File)] =
        artifacts.collect { case (a, f) if a.`type` == Artifact.DocType || a.`type` == Artifact.SourceType => (a, f) }

      val docModules = getModulesForProject(projectRef, updateClassifiersReportsFn)

      val docModulesByModuleId = docModules
        .groupBy(_.moduleId)
        .map { case (moduleId, reports) =>
          moduleId -> reports.flatMap(r => onlySourcesAndDocs(r.artifacts))
        }

      modulesWithoutDocs.map { report =>
        val matchingDocs = docModulesByModuleId.getOrElse(report.moduleId, Seq.empty)
        ModuleReportAdapter(report.moduleId, report.artifacts ++ matchingDocs)
      }
    }

    modulesWithDocs.getOrElse(modulesWithoutDocs)
  }

  private lazy val allClasspathTypes: Set[String] = projects.map(classpathTypes).reduce((a, b) => a.union(b))

  private def fixModulesIdsToSupportClassifiers(modules: Seq[ModuleReportAdapter]): Seq[ModuleReportAdapter] =
    modules.map(r => r.copy(moduleId = r.moduleId.artifacts(r.artifacts.map(_._1): _*)))

  private def groupByModuleIdentifiers(modules: Seq[ModuleReportAdapter]): mutable.LinkedHashMap[ModuleIdentifier, Seq[ModuleReportAdapter]] = {
    val modulesWithIds = modules.flatMap { module =>
      createModuleIdentifiers(module.moduleId, module.artifacts.map(_._1)).map(id => (module, id))
    }
    val result = mutable.LinkedHashMap.empty[ModuleIdentifier, mutable.ListBuffer[ModuleReportAdapter]]
    modulesWithIds.foreach { case (adapter, identifier) =>
      result.getOrElseUpdate(identifier, mutable.ListBuffer.empty) += adapter
    }
    result.map { case (id, buffer) => id -> buffer }
  }

  private def getModulesForProject(projectRef: ProjectRef, updateReportFn: ProjectRef => UpdateReportAdapter): Seq[ModuleReportAdapter] =
    projectToConfigurationsName(projectRef).flatMap(updateReportFn(projectRef).modulesFrom).filter(_.artifacts.nonEmpty)

  private def createModuleData(moduleId: ModuleIdentifier, moduleReports: Seq[ModuleReportAdapter]): ModuleData = {
    val allArtifacts = moduleReports.flatMap(_.artifacts)

    val classpathArtifacts = mutable.Set.empty[File]
    val docArtifacts = mutable.Set.empty[File]
    val sourceArtifacts = mutable.Set.empty[File]

    allArtifacts
      .filter { case (a, _) => moduleId.classifier == fuseClassifier(a) }
      .foreach { case (a, f) =>
        val artifactType = a.`type`
        if (allClasspathTypes.contains(artifactType)) {
          classpathArtifacts += f
        }
        if (artifactType == Artifact.DocType) {
          docArtifacts += f
        }
        if (artifactType == Artifact.SourceType) {
          sourceArtifacts += f
        }
      }

    ModuleData(
      moduleId,
      classpathArtifacts.toSet,
      docArtifacts.toSet,
      sourceArtifacts.toSet
    )
  }
}

object RepositoryExtractor extends SbtStateOps with TaskOps {

  def taskDef: Initialize[Task[Option[RepositoryData]]] = Def.taskDyn {
    val state = Keys.state.value
    val options = StructureKeys.sbtStructureOpts.value
    val acceptedProjects = StructureKeys.acceptedProjects.value

    Def.task {
      extractRepositoryData(state, options, acceptedProjects.toImmutableSeq)
        .onlyIf(options.download).value
    }
  }

  private def extractRepositoryData(state: State, options: Options, acceptedProjects: scala.collection.immutable.Seq[ProjectRef]): Task[RepositoryData] = {
    def classpathTypes(projectRef: ProjectRef): Set[String] =
      (projectRef / Keys.classpathTypes).getValueOrElse(state, Set.empty)

    val dependencyConfigurations = StructureKeys.dependencyConfigurations
      .forAllProjects(state, acceptedProjects)
      .toMap

    val classpathConfigurationTask = sbt.Keys.classpathConfiguration
      .forAllProjectsAndConfigurations(state, acceptedProjects, dependencyConfigurations)

    val updateAllTask: Task[Map[ProjectRef, UpdateReportAdapter]] =
      Keys.update
        .forAllProjects(state, acceptedProjects)
        .map(_.mapValues(new UpdateReportAdapter(_)).toMap)
    val updateAllClassifiersTask =
      Keys.updateClassifiers
        .forAllProjects(state, acceptedProjects)
        .map(_.mapValues(new UpdateReportAdapter(_)))
        .onlyIf(options.resolveSourceClassifiers || options.resolveJavadocClassifiers)

    for {
      updateReports <- updateAllTask
      updateClassifiersReports <- updateAllClassifiersTask
      classpathConfiguration <- classpathConfigurationTask
    } yield {

      val projectToClasspathConfig: Map[ProjectRef, Seq[Configuration]] =
        classpathConfiguration.groupBy(_._1._1)
          .mapValues(_.map { case ((_, _), c2) => c2 })
          .toMap

      val classpathAndDependencyConfigs = projectToClasspathConfig.map { case (project, configs) =>
        val projectDependencyConfigs = dependencyConfigurations.getOrElse(project, Seq.empty)
        val uniqueConfigNames = (projectDependencyConfigs ++ configs).groupBy(_.name).keys.toSeq
        project -> uniqueConfigNames
      }

      val extractor = new RepositoryExtractor(
        acceptedProjects,
        updateReports.apply,
        updateClassifiersReports.map(_.apply),
        ref => classpathTypes(ref),
        classpathAndDependencyConfigs
      )
      extractor.extract
    }
  }
}