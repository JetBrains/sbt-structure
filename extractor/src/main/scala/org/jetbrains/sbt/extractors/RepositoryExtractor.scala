package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.{ModuleData, RepositoryData}
import sbt.Project.Initialize
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class RepositoryExtractor(acceptedProjectRefs: Seq[ProjectRef],
                          updateReports: ProjectRef => UpdateReportAdapter,
                          updateClassifiersReports: Option[ProjectRef => UpdateReportAdapter],
                          classpathTypes: ProjectRef => Set[String],
                          dependencyConfigurations: ProjectRef => Seq[sbt.Configuration])
  extends ModulesOps {

  private[extractors] def extract: RepositoryData = {
    val rawModulesData = acceptedProjectRefs.flatMap(extractModules)
    val modulesData = rawModulesData.foldLeft(Seq.empty[ModuleData]) { (acc, data) =>
      acc.find(_.id == data.id) match {
        case Some(module) =>
          val newModule = ModuleData(module.id,
            module.binaries ++ data.binaries,
            module.docs ++ data.docs,
            module.sources ++ data.sources)
          acc.filterNot(_ == module) :+ newModule
        case None => acc :+ data
      }
    }
    RepositoryData(modulesData)
  }

  private def extractModules(projectRef: ProjectRef): Seq[ModuleData] = {
    val binaryReports = getModuleReports(projectRef, updateReports)

    val reportsWithDocs = updateClassifiersReports.map { updateClassifiersReportsFn =>
      def onlySourcesAndDocs(artifacts: Seq[(Artifact, File)]): Seq[(Artifact, File)] =
        artifacts.collect { case (a, f) if a.`type` == Artifact.DocType || a.`type` == Artifact.SourceType => (a, f) }

      val docAndSrcReports = getModuleReports(projectRef, updateClassifiersReportsFn)
      binaryReports.map { report =>
        val matchingDocs = docAndSrcReports.filter(_.moduleId == report.moduleId)
        val docsArtifacts = matchingDocs.flatMap(r => onlySourcesAndDocs(r.artifacts))
        new ModuleReportAdapter(report.moduleId, report.artifacts ++ docsArtifacts)
      }
    }

    merge(reportsWithDocs.getOrElse(binaryReports), classpathTypes(projectRef), Set(Artifact.DocType), Set(Artifact.SourceType))
  }

  private def getModuleReports(projectRef: ProjectRef, updateReportFn: ProjectRef => UpdateReportAdapter): Seq[ModuleReportAdapter] = {
    dependencyConfigurations(projectRef).map(_.name).flatMap(updateReportFn(projectRef).modulesFrom).filter(_.artifacts.nonEmpty)
  }

  private def merge(moduleReports: Seq[ModuleReportAdapter], classpathTypes: Set[String], docTypes: Set[String], srcTypes: Set[String]): Seq[ModuleData] = {
    val moduleReportsGrouped = moduleReports.groupBy{ rep => rep.moduleId.artifacts(rep.artifacts.map(_._1):_*) }.toSeq
    moduleReportsGrouped.flatMap { case (module, reports) =>
      val allArtifacts = reports.flatMap(_.artifacts)

      def artifacts(kinds: Set[String], classifier: String) = allArtifacts.collect {
        case (a, f) if classifier == fuseClassifier(a) && kinds.contains(a.`type`) => f
      }.toSet

      createModuleIdentifiers(module, allArtifacts.map(_._1)).map { id =>
        ModuleData(id, artifacts(classpathTypes, id.classifier),
          artifacts(docTypes, id.classifier),
          artifacts(srcTypes, id.classifier))
      }
    }
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
      Keys.update.forAllProjects(state, acceptedProjects).map(_.mapValues(new UpdateReportAdapter(_)))
    val updateAllClassifiersTask =
      Keys.updateClassifiers.forAllProjects(state, acceptedProjects).map(_.mapValues(new UpdateReportAdapter(_)))

    updateAllTask.flatMap { updateReports =>
      updateAllClassifiersTask.onlyIf(options.resolveClassifiers).map { updateClassifiersReports =>
        new RepositoryExtractor(acceptedProjects, updateReports.apply,
          updateClassifiersReports.map(_.apply), classpathTypes, dependencyConfigurations).extract
      }
    }
  }
}