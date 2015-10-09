package org.jetbrains.sbt
package processors

import org.jetbrains.sbt.Utilities._
import org.jetbrains.sbt.structure.{ModuleDependencyData, ModuleIdentifier, ProjectData}
import org.jetbrains.sbt.{structure => jb}
import sbt._

import scala.collection.mutable

/**
 * @author Nikolay Obedin
 * @since 9/15/15.
 */
object EvictionsProcessor {
  def apply(acceptedProjectRefs: Seq[ProjectRef], projectsData: Seq[ProjectData])(implicit state: State): Seq[ProjectData] =
    new EvictionsProcessor(acceptedProjectRefs, projectsData).process()
}

class EvictionsProcessor(acceptedProjectRefs: Seq[ProjectRef], projectsData: Seq[ProjectData]) extends Extractor with Configurations with Modules {

  private case class Eviction(configuration: jb.Configuration, from: ModuleIdentifier, to: ModuleIdentifier)

  private var evictions = Map.empty[String, Seq[Eviction]]
  private var dependencies = Map.empty[String, Seq[ProjectData]]
  private val evictionsApplied = mutable.HashSet.empty[String]

  private def process()(implicit state: State): Seq[ProjectData] = {
    init()
    sortByProjectDependencies(projectsData).flatMap { project =>
      if (!evictionsApplied(project.id))
        processProjectAndDeps(project, Set.empty)
      else
        Seq.empty
    }
  }

  private def processProjectAndDeps(project: ProjectData, currentEvictions: Set[Eviction]): Seq[ProjectData] = {
    val updatedEvictions = currentEvictions ++ evictions(project.id)
    val updatedProject = applyEvictions(project, updatedEvictions)
    val updatedDependencies = dependencies(project.id).flatMap(processProjectAndDeps(_, updatedEvictions))
    updatedProject +: updatedDependencies
  }

  private def applyEvictions(project: ProjectData, currentEvictions: Set[Eviction]): ProjectData = {
    evictionsApplied.add(project.id)
    currentEvictions.foldLeft(project)(applyEviction)
  }

  private def applyEviction(project: ProjectData, eviction: Eviction): ProjectData = {
    val updatedModules = project.dependencies.modules.flatMap(applyEviction(_, eviction))
    project.copy(dependencies = project.dependencies.copy(modules = updatedModules))
  }

  private def applyEviction(module: ModuleDependencyData, eviction: Eviction): Seq[ModuleDependencyData] =
    if (module.configurations.contains(eviction.configuration) && module.id == eviction.from) {
      val unaffectedConfigurations = module.configurations.filterNot(_ == eviction.configuration)
      val evictedDependency = ModuleDependencyData(eviction.to, Seq(eviction.configuration))
      if (unaffectedConfigurations.isEmpty)
        Seq(evictedDependency)
      else
        Seq(evictedDependency, ModuleDependencyData(module.id, unaffectedConfigurations))
    } else {
      Seq(module)
    }

  private def sortByProjectDependencies(projects: Seq[ProjectData]): Seq[ProjectData] =
    Dag.topologicalSort(projectsData)(p => dependencies(p.id)).reverse

  private def init()(implicit state: State): Unit = {
    dependencies = projectsData.map(p => (p.id, getDependencies(p))).toMap
    evictions = acceptedProjectRefs.map(p => (p.id, getEvictions(p))).toMap
    evictionsApplied.clear()
  }

  private def getDependencies(project: ProjectData): Seq[ProjectData] = {
    val ids = project.dependencies.projects.map(_.project)
    projectsData.filter(p => ids.contains(p.id))
  }

  private def getEvictions(projectRef: ProjectRef)(implicit state: State): Seq[Eviction] = {
    implicit val projectRefImplicit = projectRef
    projectTask(Keys.update).map { updateReport =>
      for {
        confReport <- updateReport.configurations
        if getDependencyConfigurations(state, projectRef).contains(config(confReport.configuration))
        conf = jb.Configuration(confReport.configuration)
        from <- confReport.evicted
        fromId <- toModuleIdentifiers(from)
        to <- confReport.allModules
        toId <- toModuleIdentifiers(to)
        if compareModulesWithoutVersion(fromId, toId)
      } yield {
        Eviction(conf, fromId, toId)
      }
    }.getOrElse(Seq.empty)
  }

  private def toModuleIdentifiers(moduleId: ModuleID): Seq[ModuleIdentifier] = {
    val artifacts = if (moduleId.explicitArtifacts.nonEmpty) moduleId.explicitArtifacts else Seq(Artifact("", ""))
    createModuleIdentifiers(moduleId, artifacts)
  }

  private def compareModulesWithoutVersion(fst: ModuleIdentifier, snd: ModuleIdentifier): Boolean =
    fst.name == snd.name && fst.organization == snd.organization && fst.artifactType == snd.artifactType && fst.classifier == snd.classifier
}
