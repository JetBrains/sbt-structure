package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.{ModuleData, RepositoryData}
import sbt._
import Utilities._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class RepositoryExtractor(acceptedProjectRefs: Seq[ProjectRef],
                          updateReports: ProjectRef => UpdateReport,
                          updateClassifiersReports: Option[ProjectRef => UpdateReport],
                          classpathTypes: ProjectRef => Set[String],
                          dependencyConfigurations: ProjectRef => Seq[sbt.Configuration])
  extends Modules {

  private def extract: RepositoryData = {
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
        val matchingDocs = docAndSrcReports.filter(_.module == report.module)
        val docsArtifacts = matchingDocs.flatMap { r => onlySourcesAndDocs(r.artifacts) }
        new MyModuleReport(report.module, report.artifacts ++ docsArtifacts)
      }
    }

    merge(reportsWithDocs.getOrElse(binaryReports), classpathTypes(projectRef), Set(Artifact.DocType), Set(Artifact.SourceType))
  }

  private class MyModuleReport(val module: ModuleID, val artifacts: Seq[(Artifact, File)]) {
    def this(report: ModuleReport) {
      this(report.module, report.artifacts)
    }
  }

  private def getModuleReports(projectRef: ProjectRef, updateReportFn: ProjectRef => UpdateReport): Seq[MyModuleReport] = {
    val configurationReports = {
      val relevantConfigurationNames = dependencyConfigurations(projectRef).map(_.name).toSet
      updateReportFn(projectRef).configurations.filter(report => relevantConfigurationNames.contains(report.configuration))
    }
    configurationReports.flatMap{ r => r.modules.map(new MyModuleReport(_)) }.filter(_.artifacts.nonEmpty)
  }

  private def merge(moduleReports: Seq[MyModuleReport], classpathTypes: Set[String], docTypes: Set[String], srcTypes: Set[String]): Seq[ModuleData] = {
    val moduleReportsGrouped = moduleReports.groupBy{ rep => rep.module.artifacts(rep.artifacts.map(_._1):_*) }.toSeq
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

object RepositoryExtractor extends Extractor with Configurations {
  def apply(acceptedProjectRefs: Seq[ProjectRef])(implicit state: State, options: Options): Option[RepositoryData] =
    options.download.option {
      def updateReports(projectRef: ProjectRef) =
        task(Keys.update.in(projectRef)).get
      def updateClassifiersReports(projectRef: ProjectRef) =
        task(Keys.updateClassifiers.in(projectRef)).get
      def classpathTypes(projectRef: ProjectRef) =
        setting(Keys.classpathTypes.in(projectRef)).getOrElse(Set.empty)
      def dependencyConfigurations(projectRef: ProjectRef) =
        getDependencyConfigurations(state, projectRef)

      if (options.resolveSbtClassifiers)
        new RepositoryExtractor(acceptedProjectRefs, updateReports, Some(updateClassifiersReports), classpathTypes, dependencyConfigurations).extract
      else
        new RepositoryExtractor(acceptedProjectRefs, updateReports, None, classpathTypes, dependencyConfigurations).extract
    }
}