package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.extractors.Extractor.Options
import org.jetbrains.sbt.structure.{ModuleData, RepositoryData}
import sbt._
import Utilities._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class RepositoryExtractor(acceptedProjectRefs: Seq[ProjectRef]) extends ModulesExtractor {

  override type Data = RepositoryData

  override def extract(implicit state: State, options: Options): Option[Data] =
    options.download.option {
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

  private def extractModules(projectRef: ProjectRef)(implicit state: State, options: Options): Seq[ModuleData] = {
    implicit val projectRefImplicit = projectRef

    val binaryReports = getModuleReports(Keys.update)

    lazy val reportsWithDocs = {
      def onlySourcesAndDocs(artifacts: Seq[(Artifact, File)]): Seq[(Artifact, File)] =
        artifacts.collect { case (a, f) if a.`type` == Artifact.DocType || a.`type` == Artifact.SourceType => (a, f) }

      val docAndSrcReports = getModuleReports(Keys.updateClassifiers)
      binaryReports.map { report =>
        val matchingDocs = docAndSrcReports.filter(_.module == report.module)
        val docsArtifacts = matchingDocs.flatMap { r => onlySourcesAndDocs(r.artifacts) }
        new MyModuleReport(report.module, report.artifacts ++ docsArtifacts)
      }
    }

    val classpathTypes = projectSetting(Keys.classpathTypes).get
    val modulesToMerge = if (options.resolveClassifiers) reportsWithDocs else binaryReports
    merge(modulesToMerge, classpathTypes, Set(Artifact.DocType), Set(Artifact.SourceType))
  }

  private class MyModuleReport(val module: ModuleID, val artifacts: Seq[(Artifact, File)]) {
    def this(report: ModuleReport) {
      this(report.module, report.artifacts)
    }
  }

  private def getModuleReports(task: TaskKey[UpdateReport])(implicit state: State, projectRef: ProjectRef): Seq[MyModuleReport] = {
    val configurationReports = {
      val relevantConfigurationNames = DependencyConfigurations.map(_.name).toSet
      projectTask(task).get.configurations.filter(report => relevantConfigurationNames.contains(report.configuration))
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
