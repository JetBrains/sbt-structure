package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.{ModuleData, RepositoryData}
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

object RepositoryExtractor extends SbtStateOps {
  def apply(acceptedProjectRefs: Seq[ProjectRef])(implicit state: State, options: Options): Option[RepositoryData] =
    options.download.option {
      def updateReports(projectRef: ProjectRef) =
        task(Keys.update.in(projectRef)).map(new UpdateReportAdapter(_)).get
      def updateClassifiersReports(projectRef: ProjectRef) =
        task(Keys.updateClassifiers.in(projectRef)).map(new UpdateReportAdapter(_)).get
      def classpathTypes(projectRef: ProjectRef) =
        setting(Keys.classpathTypes.in(projectRef), state).getOrElse(Set.empty)
      def dependencyConfigurations(projectRef: ProjectRef) =
        setting(StructureKeys.dependencyConfigurations.in(projectRef), state).get

      if (options.resolveSbtClassifiers)
        new RepositoryExtractor(acceptedProjectRefs, updateReports, Some(updateClassifiersReports), classpathTypes, dependencyConfigurations).extract
      else
        new RepositoryExtractor(acceptedProjectRefs, updateReports, None, classpathTypes, dependencyConfigurations).extract
    }
}