package org.jetbrains.sbt

import sbt._
import sbt.Keys._
import sbt.Value
import sbt.BuildStructure
import Utilities._
import Android._

/**
 * @author Pavel Fatin
 */
object Extractor {
  private val ExportableConfigurations = Seq(Compile, Test, IntegrationTest)
  private val DependencyConfigurations = Seq(Compile, Test, Runtime, Provided, Optional)

  def extractStructure(state: State, download: Boolean, resolveClassifiers: Boolean, resolveSbtClassifiers: Boolean): StructureData = {
    val structure = Project.extract(state).structure

    val sbtVersion = Keys.sbtVersion.get(structure.data).get

    val scalaData = extractScala(state)

    val allProjectRefs = structure.allProjectRefs

    val projectsData = allProjectRefs.map(extractProject(state, structure, _, download && resolveSbtClassifiers))

    val repositoryData = download.option {
      val rawModulesData = allProjectRefs.flatMap(extractModules(state, structure, _, resolveClassifiers))
      val modulesData = rawModulesData.foldLeft(Seq.empty[ModuleData]) { (acc, data) =>
        acc.find(_.id == data.id) match {
          case Some(module) =>
            val newModule = ModuleData(module.id, module.binaries ++ data.binaries,
                                                  module.docs ++ data.docs,
                                                  module.sources ++ data.sources)
            acc.filterNot(_ == module) :+ newModule
          case None => acc :+ data
        }
      }
      RepositoryData(modulesData)
    }

    val localCachePath = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))

    StructureData(sbtVersion, scalaData, projectsData, repositoryData, localCachePath)
  }

  def extractScala(state: State): ScalaData = {
    val provider = state.configuration.provider.scalaProvider
    val libraryJar = provider.libraryJar
    val compilerJar = provider.compilerJar
    val extraJars = provider.jars.filter(_.getName.contains("reflect")).toSet - libraryJar - compilerJar
    ScalaData(provider.version, libraryJar, provider.compilerJar, extraJars.toSeq, Seq.empty)
  }

  def extractProject(state: State, structure: BuildStructure, projectRef: ProjectRef, download: Boolean): ProjectData = {
    val id = projectRef.project

    val name = Keys.name.in(projectRef, Compile).get(structure.data).get

    val organization = Keys.organization.in(projectRef, Compile).get(structure.data).get

    val version = Keys.version.in(projectRef, Compile).get(structure.data).get

    val base = Keys.baseDirectory.in(projectRef, Compile).get(structure.data).get

    val target = Keys.target.in(projectRef, Compile).get(structure.data).get

    val configurations = ExportableConfigurations.flatMap(extractConfiguration(state, structure, projectRef, _))

    val java = {
      val home = Keys.javaHome.in(projectRef, Compile).get(structure.data).get

      val options: Seq[String] = Project.runTask(javacOptions.in(projectRef, Compile), state) match {
        case Some((_, Value(it))) => it
        case _ => Seq.empty
      }

      if (home.isDefined || options.nonEmpty) Some(JavaData(home, options)) else None
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
      val (docs, sources) = if (download) extractSbtClassifiers(state, projectRef) else (Seq.empty, Seq.empty)
      BuildData(unit.imports, unit.classpath, docs, sources)
    }

    val dependencies = extractDependencies(state, structure, projectRef)
    val resolvers = extractResolvers(state, projectRef)

    val android = extractAndroid(structure, projectRef, state)

    val play2 = new Play2Extractor(structure, projectRef, state).extract()

    ProjectData(id, name, organization, version, base, target, build, configurations, java, scala, android,
      dependencies, resolvers, play2)
  }

  def extractConfiguration(state: State, structure: BuildStructure, projectRef: ProjectRef, configuration: Configuration): Option[ConfigurationData] = {
    Keys.classDirectory.in(projectRef, configuration).get(structure.data).map { output =>
      val sources = {
        val managed = Keys.managedSourceDirectories.in(projectRef, configuration).get(structure.data).get
        val unmanaged = Keys.unmanagedSourceDirectories.in(projectRef, configuration).get(structure.data).get
        managed.map(DirectoryData(_, managed = true)) ++ unmanaged.map(DirectoryData(_, managed = false))
      }

      val resources = {
        val managed = Keys.managedResourceDirectories.in(projectRef, configuration).get(structure.data).get
        val unmanaged = Keys.unmanagedResourceDirectories.in(projectRef, configuration).get(structure.data).get
        managed.map(DirectoryData(_, managed = true)) ++ unmanaged.map(DirectoryData(_, managed = false))
      }

      ConfigurationData(configuration.name, sources, resources, output)
    }
  }

  def extractDependencies(state: State, structure: BuildStructure, projectRef: ProjectRef): DependencyData = {
    val projectDependencies = {
      val project = Project.getProject(projectRef, structure).get
      project.dependencies.map(it => ProjectDependencyData(it.project.project, it.configuration))
    }

    val moduleDependencies = moduleDependenciesIn(state, projectRef)

    val jarDependencies = jarDependenciesIn(state, projectRef)

    DependencyData(projectDependencies, moduleDependencies, jarDependencies)
  }

  def moduleDependenciesIn(state: State, projectRef: ProjectRef): Seq[ModuleDependencyData] = {
    def modulesIn(configuration: Configuration): Seq[ModuleID] = {
      Project.runTask(externalDependencyClasspath.in(projectRef, configuration), state) match {
        case Some((_, Value(attrs))) =>
          for {
            attr <- attrs
            module <- attr.get(Keys.moduleID.key)
            artifact <- attr.get(Keys.artifact.key)
          } yield module.artifacts(artifact)
        case _ => Seq.empty
      }
    }

    val moduleToConfigurations = DependencyConfigurations
      .flatMap(configuration => modulesIn(configuration).map(module => (module, configuration)))
      .groupBy(_._1)
      .mapValues(_.unzip._2)
      .toSeq

    moduleToConfigurations.map { case (moduleId, configurations) =>
      ModuleDependencyData(
        createModuleIdentifier(moduleId, moduleId.explicitArtifacts.headOption),
        mapConfigurations(configurations))
    }.foldLeft(Seq.empty[ModuleDependencyData]) { (acc, moduleData) =>
      acc.find(_.id == moduleData.id) match {
        case Some(foundModuleData) =>
          val newModuleData = ModuleDependencyData(moduleData.id,
            mapConfigurations(moduleData.configurations ++ foundModuleData.configurations))
          acc.filterNot(_ == foundModuleData) :+ newModuleData
        case None => acc :+ moduleData
      }
    }
  }

  private def createModuleIdentifier(moduleId: ModuleID, artifact: Option[Artifact]): ModuleIdentifier = {
    val fusingClassifiers = Seq("", "sources", "javadoc")
    def fuseClassifier(artifact: Artifact): String = artifact.classifier match {
      case Some(classifier) if fusingClassifiers.contains(classifier) => fusingClassifiers.head
      case Some(classifier) => classifier
      case None => fusingClassifiers.head
    }

    val artifactType = "jar"
    val classifier   = artifact map fuseClassifier getOrElse fusingClassifiers.head
    ModuleIdentifier(moduleId.organization, moduleId.name, moduleId.revision, artifactType, classifier)
  }

  def jarDependenciesIn(state: State, projectRef: ProjectRef): Seq[JarDependencyData] = {
    def jarsIn(configuration: Configuration): Seq[File] = {
      val classpath: Option[Classpath] = Project.runTask(unmanagedJars.in(projectRef, configuration), state) collect {
        case (_, Value(it)) => it
      }
      classpath.map(_.map(_.data)).getOrElse(Seq.empty)
    }

    val jarToConfigurations = DependencyConfigurations
      .flatMap(configuration => jarsIn(configuration).map(file => (file, configuration)))
      .groupBy(_._1)
      .mapValues(_.unzip._2)
      .toSeq

    jarToConfigurations.map { case (file, configurations) =>
      JarDependencyData(file, mapConfigurations(configurations))
    }
  }

  // We have to perform this configurations mapping because we're using externalDependencyClasspath
  // rather than libraryDependencies (to acquire transitive dependencies),  so we detect
  // module presence (in external classpath) instead of explicitly declared configurations.
  def mapConfigurations(configurations: Seq[Configuration]): Seq[Configuration] = {
    val cs = configurations.toSet

    if (cs == Set(Compile, Test, Runtime)) {
      Seq.empty
    } else if (cs == Set(Compile, Test)) {
      Seq(Provided)
    } else {
      configurations
    }
  }

  def extractModules(state: State, structure: BuildStructure, projectRef: ProjectRef, resolveClassifiers: Boolean): Seq[ModuleData] = {
    def run[T](task: ScopedKey[Task[T]]): T = {
      Project.runTask(task, state) collect {
        case (_, Value(it)) => it
      } getOrElse sys.error(s"Couldn't run: $task")
    }
    def getModuleReports(task: TaskKey[UpdateReport]): Seq[ModuleReport] = {
      val updateReport: UpdateReport = run(task in projectRef)
      val configurationReports = {
        val relevantConfigurationNames = DependencyConfigurations.map(_.name).toSet
        updateReport.configurations.filter(report => relevantConfigurationNames.contains(report.configuration))
      }

      configurationReports.flatMap(_.modules).filter(_.artifacts.nonEmpty)
    }

    val moduleReports =
      if (resolveClassifiers) {
        val reports = getModuleReports(updateClassifiers)
        def onlySourcesAndDocs(artifacts: Seq[Artifact]): Boolean =
          artifacts.forall { a => a.`type` == "src" || a.`type` == "doc" }
        // `updateClassifiers` doesn't resolve dependencies with non-empty
        // classifiers so we get them from `update`; but only them - otherwise
        // some jars may be duplicated (e.g. scala-library from .sbt and .ivy)
        reports ++ getModuleReports(update).filter { r =>
          reports.forall { m => m.module != r.module || onlySourcesAndDocs(m.artifacts.map(_._1)) } ||
            r.artifacts.flatMap(_._1.classifier).nonEmpty
        }
      } else
        getModuleReports(update)

    val classpathTypes = Keys.classpathTypes.in(projectRef).get(structure.data).get

    merge(moduleReports, classpathTypes, Set("doc"), Set("src"))
  }

  private def merge(moduleReports: Seq[ModuleReport], classpathTypes: Set[String], docTypes: Set[String], srcTypes: Set[String]): Seq[ModuleData] = {
    val moduleReportsGrouped = moduleReports.groupBy{ rep => rep.module.artifacts(rep.artifacts.map(_._1):_*) }.toSeq
    moduleReportsGrouped.map { case (module, reports) =>
      val allArtifacts = reports.flatMap(_.artifacts)
      def artifacts(kinds: Set[String]) = allArtifacts.collect { case (a, f) if kinds contains a.`type` => f }.toSet

      val id = createModuleIdentifier(module, allArtifacts.headOption.map(_._1))
      ModuleData(id, artifacts(classpathTypes), artifacts(docTypes), artifacts(srcTypes))
    }
  }

  def extractSbtClassifiers(state: State, projectRef: ProjectRef): (Seq[File], Seq[File]) = {
    val updateReport: UpdateReport = Project.runTask(updateSbtClassifiers.in(projectRef), state) collect {
      case (_, Value(it)) => it
    } getOrElse {
      throw new RuntimeException()
    }

    val allArtifacts = updateReport.configurations.flatMap(_.modules.flatMap(_.artifacts))

    def artifacts(kind: String) = allArtifacts.filter(_._1.`type` == kind).map(_._2).distinct

    (artifacts("doc"), artifacts("src"))
  }

  def extractResolvers(state: State, projectRef: ProjectRef): Set[ResolverData] =
    Project.runTask(fullResolvers.in(projectRef, configuration), state) match {
      case Some((_, Value(resolvers))) => resolvers.map({
        case MavenRepository(name, root) => Some(ResolverData(name, root))
        case _ => None
      }).flatten.toSet
      case _ => Set.empty
    }
}
