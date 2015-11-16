package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.{ModuleIdentifier, ModuleData, RepositoryData}
import sbt.Project.Initialize
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class RepositoryExtractor(projects: Seq[ProjectRef],
                          updateReports: ProjectRef => UpdateReportAdapter,
                          updateClassifiersReports: Option[ProjectRef => UpdateReportAdapter],
                          classpathTypes: ProjectRef => Set[String],
                          dependencyConfigurations: ProjectRef => Seq[sbt.Configuration])
  extends ModulesOps {

  private[extractors] def extract: RepositoryData = {
    val modules = fixModulesIdsToSupportClassifiers(allModulesWithDocs)
    RepositoryData(groupByModuleIdentifiers(modules).toSeq.map((createModuleData _).tupled))
  }

  private def allModulesWithDocs: Seq[ModuleReportAdapter] = projects.flatMap { projectRef =>
    val modulesWithoutDocs = getModulesForProject(projectRef, updateReports)

    val modulesWithDocs = updateClassifiersReports.map { updateClassifiersReportsFn =>
      def onlySourcesAndDocs(artifacts: Seq[(Artifact, File)]): Seq[(Artifact, File)] =
        artifacts.collect { case (a, f) if a.`type` == Artifact.DocType || a.`type` == Artifact.SourceType => (a, f) }

      val docModules = getModulesForProject(projectRef, updateClassifiersReportsFn)
      modulesWithoutDocs.map { report =>
        val matchingDocs = docModules.filter(_.moduleId == report.moduleId).flatMap(r => onlySourcesAndDocs(r.artifacts))
        new ModuleReportAdapter(report.moduleId, report.artifacts ++ matchingDocs)
      }
    }

    modulesWithDocs.getOrElse(modulesWithoutDocs)
  }

  private def allClasspathTypes: Set[String] = projects.map(classpathTypes).reduce((a, b) => a.union(b))

  private def fixModulesIdsToSupportClassifiers(modules: Seq[ModuleReportAdapter]): Seq[ModuleReportAdapter] =
    modules.map(r => r.copy(moduleId = r.moduleId.artifacts(r.artifacts.map(_._1):_*)))

  private def groupByModuleIdentifiers(modules: Seq[ModuleReportAdapter]): Map[ModuleIdentifier, Seq[ModuleReportAdapter]] = {
    val modulesWithIds = modules.flatMap { module =>
      createModuleIdentifiers(module.moduleId, module.artifacts.map(_._1)).map(id => (module, id))
    }
    modulesWithIds.groupBy(_._2).mapValues(_.unzip._1)
  }

  private def getModulesForProject(projectRef: ProjectRef, updateReportFn: ProjectRef => UpdateReportAdapter): Seq[ModuleReportAdapter] =
    dependencyConfigurations(projectRef).map(_.name).flatMap(updateReportFn(projectRef).modulesFrom).filter(_.artifacts.nonEmpty)

  private def createModuleData(moduleId: ModuleIdentifier, moduleReports: Seq[ModuleReportAdapter]): ModuleData = {
    val allArtifacts = moduleReports.flatMap(_.artifacts)

    def artifacts(kinds: Set[String]) = allArtifacts.collect {
      case (a, f) if moduleId.classifier == fuseClassifier(a) && kinds.contains(a.`type`) => f
    }.toSet

    ModuleData(moduleId, artifacts(allClasspathTypes), artifacts(Set(Artifact.DocType)), artifacts(Set(Artifact.SourceType)))
  }
}

object RepositoryExtractor extends SbtStateOps with TaskOps {

  def taskDef: Initialize[Task[Option[RepositoryData]]] =
    (Keys.state, StructureKeys.sbtStructureOpts, StructureKeys.acceptedProjects).flatMap {
      (state, options, acceptedProjects) =>
        extractRepositoryData(state, options, acceptedProjects).onlyIf(options.download)
    }

  private def extractRepositoryData(state: State, options: Options, acceptedProjects: Seq[ProjectRef]): Task[RepositoryData] = {
    def classpathTypes(projectRef: ProjectRef) =
      Keys.classpathTypes.in(projectRef).getOrElse(state, Set.empty)
    def dependencyConfigurations(projectRef: ProjectRef) =
      StructureKeys.dependencyConfigurations.in(projectRef).get(state)

    val updateAllTask =
      Keys.update
        .forAllProjects(state, acceptedProjects)
        .map(_.mapValues(new UpdateReportAdapter(_)))
    val updateAllClassifiersTask =
      Keys.updateClassifiers
        .forAllProjects(state, acceptedProjects)
        .map(_.mapValues(new UpdateReportAdapter(_)))
        .onlyIf(options.resolveClassifiers)

    for {
      updateReports <- updateAllTask
      updateClassifiersReports <- updateAllClassifiersTask
    } yield {
      new RepositoryExtractor(acceptedProjects, updateReports.apply,
        updateClassifiersReports.map(_.apply), classpathTypes, dependencyConfigurations).extract
    }
  }
}