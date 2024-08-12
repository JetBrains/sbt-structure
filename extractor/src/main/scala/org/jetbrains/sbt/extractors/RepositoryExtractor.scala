package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.structure.{ModuleData, ModuleIdentifier, RepositoryData}
import org.jetbrains.sbt.{ModuleReportAdapter, ModulesOps, Options, SbtStateOps, StructureKeys, TaskOps, UpdateReportAdapter}
import sbt.Def.Initialize
import sbt.{Def, _}

import scala.collection.mutable

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
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
    val modulesData = modulesReportsByIdentifier.toSeq.map((createModuleData _).tupled)
    RepositoryData(modulesData)
  }

  private def allModulesWithDocs: Seq[ModuleReportAdapter] = projects.flatMap(moduleWithDoc)

  private def moduleWithDoc(projectRef: ProjectRef): Seq[ModuleReportAdapter] = {

    val modulesWithoutDocs = getModulesForProject(projectRef, updateReports)

    val modulesWithDocs = updateClassifiersReports.map { updateClassifiersReportsFn =>
      def onlySourcesAndDocs(artifacts: Seq[(Artifact, File)]): Seq[(Artifact, File)] =
        artifacts.collect { case (a, f) if a.`type` == Artifact.DocType || a.`type` == Artifact.SourceType => (a, f) }

      val docModules = getModulesForProject(projectRef, updateClassifiersReportsFn)
      modulesWithoutDocs.map { report =>
        val matchingDocs = docModules.filter(_.moduleId == report.moduleId).flatMap(r => onlySourcesAndDocs(r.artifacts))
        ModuleReportAdapter(report.moduleId, report.artifacts ++ matchingDocs)
      }
    }

    modulesWithDocs.getOrElse(modulesWithoutDocs)
  }

  private def allClasspathTypes: Set[String] = projects.map(classpathTypes).reduce((a, b) => a.union(b))

  private def fixModulesIdsToSupportClassifiers(modules: Seq[ModuleReportAdapter]): Seq[ModuleReportAdapter] =
    modules.map(r => r.copy(moduleId = r.moduleId.artifacts(r.artifacts.map(_._1):_*)))

  private def groupByModuleIdentifiers(modules: Seq[ModuleReportAdapter]): mutable.LinkedHashMap[ModuleIdentifier, Seq[ModuleReportAdapter]] = {
    val modulesWithIds = modules.flatMap { module =>
      createModuleIdentifiers(module.moduleId, module.artifacts.map(_._1)).map(id => (module, id))
    }
    val result = mutable.LinkedHashMap.empty[ModuleIdentifier, Seq[ModuleReportAdapter]]
    modulesWithIds.foreach { case (adapter, identifier) =>
      val adapters = result.getOrElse(identifier, Seq.empty)
      result(identifier) = adapters :+ adapter
    }
    result
  }

  private def getModulesForProject(projectRef: ProjectRef, updateReportFn: ProjectRef => UpdateReportAdapter): Seq[ModuleReportAdapter] =
    projectToConfigurationsName(projectRef).flatMap(updateReportFn(projectRef).modulesFrom).filter(_.artifacts.nonEmpty)

  private def createModuleData(moduleId: ModuleIdentifier, moduleReports: Seq[ModuleReportAdapter]): ModuleData = {
    val allArtifacts = moduleReports.flatMap(_.artifacts)

    def artifacts(kinds: Set[String]) = allArtifacts.collect {
      case (a, f) if moduleId.classifier == fuseClassifier(a) && kinds.contains(a.`type`) => f
    }.toSet

    ModuleData(
      moduleId,
      artifacts(allClasspathTypes),
      artifacts(Set(Artifact.DocType)),
      artifacts(Set(Artifact.SourceType))
    )
  }
}

object RepositoryExtractor extends SbtStateOps with TaskOps {

  def taskDef: Initialize[Task[Option[RepositoryData]]] = Def.taskDyn {
    val state = Keys.state.value
    val options = StructureKeys.sbtStructureOpts.value
    val acceptedProjects = StructureKeys.acceptedProjects.value

    Def.task {
      extractRepositoryData(state, options, acceptedProjects)
        .onlyIf(options.download).value
    }
  }

  private def extractRepositoryData(state: State, options: Options, acceptedProjects: Seq[ProjectRef]): Task[RepositoryData] = {
    def classpathTypes(projectRef: ProjectRef) =
      Keys.classpathTypes.in(projectRef).getOrElse(state, Set.empty)

    val dependencyConfigurations = StructureKeys.dependencyConfigurations
      .forAllProjects(state, acceptedProjects)
      .toMap

    val classpathConfigurationTask = sbt.Keys.classpathConfiguration
      .forAllProjectsAndConfigurations(state, acceptedProjects, dependencyConfigurations)

    val updateAllTask: Task[Map[ProjectRef, UpdateReportAdapter]] =
      Keys.update
        .forAllProjects(state, acceptedProjects)
        .map(_.mapValues(new UpdateReportAdapter(_)))
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

      def extractClasspathConfigs(projectToConfigTuple: Seq[((ProjectRef, Configuration), Configuration)]): Seq[Configuration] =
        projectToConfigTuple.map { case ((_, _), c2) => c2 }

      val projectToClasspathConfig = classpathConfiguration.groupBy(_._1._1)
        .mapValues(extractClasspathConfigs)

      val classpathAndDependencyConfigs = projectToClasspathConfig.map { case (project, configs) =>
        val projectDependencyConfigs = dependencyConfigurations.getOrElse(project, Seq.empty)
        val uniqueConfigNames = (projectDependencyConfigs ++ configs).groupBy(_.name).keys.toSeq
        project -> uniqueConfigNames
      }

      val extractor = new RepositoryExtractor(
        acceptedProjects,
        updateReports.apply,
        updateClassifiersReports.map(_.apply),
        classpathTypes,
        classpathAndDependencyConfigs
      )
      extractor.extract
    }
  }
}