package org.jetbrains.sbt

import sbt._
import sbt.Keys._
import sbt.Value
import sbt.BuildStructure
import Utilities._

/**
 * @author Pavel Fatin
 */
object Extractor {
  private val ExportableConfigurations = Seq(Compile, Test, IntegrationTest)
  private val DependencyConfigurations = Seq(Compile, Test, Runtime, Provided)

  def extractStructure(state: State, download: Boolean, resolveClassifiers: Boolean, resolveSbtClassifiers: Boolean): StructureData = {
    val structure = Project.extract(state).structure

    val sbtVersion = Keys.sbtVersion.get(structure.data).get

    val scalaData = extractScala(state)

    val allProjectRefs = structure.allProjectRefs

    val projectsData = allProjectRefs.map(extractProject(state, structure, _, download && resolveSbtClassifiers))

    val repositoryData = download.option {
      val modulesData = allProjectRefs.flatMap(extractModules(state, structure, _, resolveClassifiers)).distinctBy(_.id)
      RepositoryData(modulesData)
    }

    StructureData(sbtVersion, scalaData, projectsData, repositoryData)
  }

  def extractScala(state: State): ScalaData = {
    val provider = state.configuration.provider.scalaProvider
    val libraryJar = provider.libraryJar
    val compilerJar = provider.compilerJar
    val extraJars = provider.jars.filter(_.getName.contains("reflect")).toSet - libraryJar - compilerJar
    ScalaData(provider.version, libraryJar, provider.compilerJar, extraJars.toSeq, Seq.empty)
  }

  /**
   * @author Nikolay Stanchenko (adapted from https://github.com/mpeltonen/sbt-idea/blob/sbt-0.13/src/main/scala/org/sbtidea/android/AndroidSupport.scala)
   */
  def isAndroid(structure: BuildStructure, projectRef: ProjectRef): Boolean = {
    import android.Keys

    println(Keys.manifestPath.in(projectRef, Keys.Android).get(structure.data))

    Keys.manifestPath.in(projectRef, Keys.Android).get(structure.data) match {
      case Some(f) if f.exists => true
      case _ => false
    }
  }

  /**
   * @author Nikolay Stanchenko (adapted from https://github.com/mpeltonen/sbt-idea/blob/sbt-0.13/src/main/scala/org/sbtidea/android/AndroidSupport.scala)
   */
  def extractAndroidJdk(structure: BuildStructure, projectRef: ProjectRef): JdkData = {
    import android.Keys

    def platform(v: Int) = s"Android API $v Platform"
    val version = Keys.targetSdkVersion.in(projectRef, Keys.Android).get(structure.data).get
    JdkData(platform(version), "Android SDK")
  }

  /**
   * @author Nikolay Stanchenko (adapted from https://github.com/mpeltonen/sbt-idea/blob/sbt-0.13/src/main/scala/org/sbtidea/android/AndroidSupport.scala)
   */
  def extractAndroid(structure: BuildStructure, projectRef: ProjectRef): AndroidData = {
    import android.Keys

    val manifest = Keys.manifestPath.in(projectRef, Keys.Android).get(structure.data).get
    val apk = Keys.apkFile.in(projectRef, Keys.Android).get(structure.data)
    val library = Keys.libraryProject.in(projectRef, Keys.Android).get(structure.data).get

    // for some reason projectLayout is not calculated automatically, force it
    val layout = Keys.projectLayout.in(projectRef, Keys.Android).get(structure.data) getOrElse
      Keys.ProjectLayout(new File(projectRef.build))

    AndroidData(manifest, apk, layout.res, layout.assets, layout.libs, layout.gen, library)
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

      // maybe add Android JDK
      val jdk = if (isAndroid(structure, projectRef)) {
        Some(extractAndroidJdk(structure, projectRef))
      } else None

      if (home.isDefined || options.nonEmpty || jdk.nonEmpty) Some(JavaData(home, options, jdk)) else None
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

    // maybe enable Android facet
    val android: Option[AndroidData] = if (isAndroid(structure, projectRef)) {
      Some(extractAndroid(structure, projectRef))
    } else None

    val build = {
      val unit = structure.units(projectRef.build)
      val (docs, sources) = if (download) extractSbtClassifiers(state, projectRef) else (Seq.empty, Seq.empty)
      BuildData(unit.imports, unit.classpath, docs, sources)
    }

    val dependencies = extractDependencies(state, structure, projectRef)

    ProjectData(id, name, organization, version, base, target, build, configurations, java, scala, android, dependencies)
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
        case Some((_, Value(it))) => it.flatMap(_.get(Keys.moduleID.key))
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
        ModuleIdentifier(moduleId.organization, moduleId.name, moduleId.revision),
        scopeFor(configurations))
    }
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
      JarDependencyData(file, scopeFor(configurations))
    }
  }

  def scopeFor(configurations: Seq[Configuration]): Option[String] = {
    val mapped = map(configurations)
    if (mapped.nonEmpty) Some(mapped.mkString(";")) else None
  }
  
  // We have to perform this configurations mapping because we're using externalDependencyClasspath
  // rather than libraryDependencies (to acquire transitive dependencies),  so we detect
  // module presence (in external classpath) instead of explicitly declared configurations.
  def map(configurations: Seq[Configuration]): Seq[Configuration] = {
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

    val moduleReports = getModuleReports(update) ++ 
      (if (resolveClassifiers) getModuleReports(updateClassifiers) else Seq.empty)
    
    val classpathTypes = Keys.classpathTypes.in(projectRef).get(structure.data).get

    merge(moduleReports, classpathTypes, Set("doc"), Set("src"))
  }

  private def merge(moduleReports: Seq[ModuleReport], classpathTypes: Set[String], docTypes: Set[String], srcTypes: Set[String]): Seq[ModuleData] = {
    moduleReports.groupBy(_.module).toSeq.map { case (module, reports) =>
      val id = ModuleIdentifier(module.organization, module.name, module.revision)

      val allArtifacts = reports.flatMap(_.artifacts)

      def artifacts(kinds: Set[String]) = allArtifacts.collect { case (a, f) if kinds contains a.`type` => f }.distinct

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
}
