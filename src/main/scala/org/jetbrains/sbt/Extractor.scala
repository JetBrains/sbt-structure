package org.jetbrains.sbt

import sbt.Keys._
import sbt._
import sbt.Load.BuildStructure
import sbt.Value
import Utilities._
import scala.collection.mutable

/**
 * @author Pavel Fatin
 */
object Extractor {
  def extractStructure(state: State, download: Boolean): StructureData = {
    val structure = Project.extract(state).structure

    val scalaData = extractScala(state)

    val projectData = extractProject(state, structure, Project.current(state))(mutable.HashMap.empty, mutable.HashSet.empty)

    val repositoryData = download.option {
      val modulesData = structure.allProjectRefs.flatMap(extractModules(state, _)).distinctBy(_.id)
      RepositoryData(modulesData)
    }

    StructureData(scalaData, projectData, repositoryData)
  }

  def extractScala(state: State): ScalaData = {
    val provider = state.configuration.provider.scalaProvider
    val libraryJar = provider.libraryJar
    val compilerJar = provider.compilerJar
    val extraJars = provider.jars.filter(_.getName.contains("reflect")).toSet - libraryJar - compilerJar
    ScalaData(provider.version, libraryJar, provider.compilerJar, extraJars.toSeq, Seq.empty)
  }

  private def mapProjectDependencies[T](project: ResolvedProject, state: State, structure: BuildStructure)
                                       (map: ProjectData => T)
                                       (implicit extractedMap: mutable.HashMap[ProjectRef, ProjectData],
                                                 writtenProjects: mutable.HashSet[String]): Seq[T] = {
    project.dependencies.flatMap {
      case ResolvedClasspathDependency(projectRef, conf) => Seq(map(extractProject(state, structure, projectRef)))
      case _ => Seq.empty
    }
  }

  private def filterExistingProjects(projects: Seq[ProjectData])
                                    (implicit writtenProjects: mutable.HashSet[String]): Seq[ProjectData] = {
    projects.flatMap {
      case project if !writtenProjects.contains(project.name) => Seq(project)
      case _ => Seq.empty
    }
  }

  def extractProject(state: State, structure: BuildStructure, projectRef: ProjectRef)
                    (implicit extractedMap: mutable.HashMap[ProjectRef, ProjectData],
                              writtenProjects: mutable.HashSet[String]): ProjectData = {
    extractedMap.get(projectRef) match {
      case Some(data) => return data
      case _ =>
    }

    val name = Keys.name.in(projectRef, Compile).get(structure.data).get

    val organization = Keys.organization.in(projectRef, Compile).get(structure.data).get

    val version = Keys.version.in(projectRef, Compile).get(structure.data).get

    val base = Keys.baseDirectory.in(projectRef, Compile).get(structure.data).get

    val configurations = Seq(
      extractConfiguration(state, structure, projectRef, Compile),
      extractConfiguration(state, structure, projectRef, Test),
      extractConfiguration(state, structure, projectRef, Runtime))

    val java = {
      val home = Keys.javaHome.in(projectRef, Compile).get(structure.data).get

      val options: Seq[String] = Project.runTask(javacOptions.in(projectRef, Compile), state) match {
        case Some((_, Value(it))) => it
        case _ => Seq.empty
      }

      home.map(JavaData(_, options))
    }

    val scala: Option[ScalaData] = {
      val options: Seq[String] = Project.runTask(scalacOptions.in(projectRef, Compile), state) match {
        case Some((_, Value(it))) => it
        case _ => Seq.empty
      }

      Project.runTask(scalaInstance.in(projectRef, Compile), state) collect {
        case (_, Value(instance)) =>
          val extraJars = instance.extraJars.filter(_.getName.contains("reflect"))
          ScalaData(instance.version, instance.libraryJar, instance.compilerJar, extraJars, options)
      }
    }

    val build = {
      val unit = structure.units(projectRef.build)
      val classpath = unit.classpath
      BuildData(classpath, unit.imports)
    }

    val project = Project.getProject(projectRef, structure).get

    val projects = filterExistingProjects(project.aggregate.map(extractProject(state, structure, _)) ++
      mapProjectDependencies(project, state, structure)(identity))

    val data = ProjectData(name, organization, version, base, build, configurations, java, scala, projects)
    extractedMap += ((projectRef, data))
    writtenProjects ++= projects.map(_.name)
    data
  }

  def extractConfiguration(state: State, structure: BuildStructure, projectRef: ProjectRef, configuration: Configuration)
                          (implicit extractedMap: mutable.HashMap[ProjectRef, ProjectData],
                                    writtenProjects: mutable.HashSet[String]): ConfigurationData = {
    val sources = Keys.sourceDirectories.in(projectRef, configuration).get(structure.data).get

    val resources = Keys.resourceDirectories.in(projectRef, configuration).get(structure.data).get

    val output = Keys.classDirectory.in(projectRef, configuration).get(structure.data).get

    val moduleDependencies = {
      val classpath: Option[Classpath] = Project.runTask(externalDependencyClasspath.in(projectRef, configuration), state) collect {
        case (_, Value(it)) => it
      }

      val moduleIDs = classpath.get.flatMap(_.get(Keys.moduleID.key))

      moduleIDs.map(it => ModuleIdentifier(it.organization, it.name, it.revision))
    }

    val projectDependencies = mapProjectDependencies(Project.getProject(projectRef, structure).get, state, structure)(_.name)

    val jarDependencies: Seq[File] = {
      val classpath: Option[Classpath] = Project.runTask(unmanagedJars.in(projectRef, configuration), state) collect {
        case (_, Value(it)) => it
      }
      classpath.get.map(_.data)
    }

    ConfigurationData(configuration.name, sources, resources, output, projectDependencies, moduleDependencies, jarDependencies)
  }

  def extractModules(state: State, projectRef: ProjectRef): Seq[ModuleData] = {
    def run(task: TaskKey[UpdateReport]): Seq[ModuleReport] = {
      val updateReport: UpdateReport = Project.runTask(task.in(projectRef), state) collect {
        case (_, Value(it)) => it
      } getOrElse {
        throw new RuntimeException()
      }

      updateReport.configurations.flatMap(_.modules).filter(_.artifacts.nonEmpty)
    }

    val moduleReports = run(update) ++ run(updateClassifiers) //++ run(updateSbtClassifiers)

    merge(moduleReports)
  }

  private def merge(moduleReports: Seq[ModuleReport]): Seq[ModuleData] = {
    moduleReports.groupBy(_.module).toSeq.map { case (module, reports) =>
      val id = ModuleIdentifier(module.organization, module.name, module.revision)

      val allArtifacts = reports.flatMap(_.artifacts)

      def artifacts(kind: String) = allArtifacts.filter(_._1.`type` == kind).map(_._2).distinct

      ModuleData(id, artifacts("jar"), artifacts("doc"), artifacts("src"))
    }
  }
}
