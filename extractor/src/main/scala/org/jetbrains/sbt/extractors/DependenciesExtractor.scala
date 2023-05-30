package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import sbt._
import sbt.jetbrains.apiAdapter._

import scala.collection.Seq

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */

class DependenciesExtractor(projectRef: ProjectRef,
                            buildDependencies: BuildDependencies,
                            unmanagedClasspath: sbt.Configuration => Keys.Classpath,
                            externalDependencyClasspath: Option[sbt.Configuration => Keys.Classpath],
                            dependencyConfigurations: Seq[sbt.Configuration],
                            testConfigurations: Seq[sbt.Configuration],
                            sourceConfigurations: Seq[sbt.Configuration],
                            insertProjectTransitiveDependencies: Boolean,
                            projectToConfigurations: Map[ProjectRef, Seq[jb.Configuration]])
  extends ModulesOps {

  private[extractors] def extract: DependencyData = {
    val projectDependencies = if (insertProjectTransitiveDependencies)
      transitiveProjectDependencies
    else
      nonTransitiveProjectDependencies
    DependencyData(projectDependencies, moduleDependencies, jarDependencies)
  }

  private def transitiveProjectDependencies: Dependencies = {
    val dependencies = projectToConfigurations.map { case (project, configurations) =>
      val IDEAScopes = Seq(jb.Configuration.Compile, jb.Configuration.Runtime, jb.Configuration.Provided).map(_.name)
      val sourceConfigurationsNames = sourceConfigurations.map(_.name)
      val transformedConfigurations = mapConfigurations(configurations).map { configuration =>
        // additional mapping is required in case of situation where project1 dependsOn project2 in two custom configurations -> the test one
        // and the one containing sources. Without this mapping custom test configuration will be transformed to TEST in #mapConfigurations and in the scala plugin, scope
        // for this dependency would be TEST which would not be truth
      if (!IDEAScopes.contains(configuration.name) && sourceConfigurationsNames.contains(configuration.name)) {
          jb.Configuration.Compile
        } else {
          configuration
        }
      }
      ProjectDependencyData(project.id, Some(project.build), transformedConfigurations)
    }.toSeq
    Dependencies(Nil, dependencies)
  }

  private def nonTransitiveProjectDependencies: Dependencies = {
    val dependencies = buildDependencies.classpath.getOrElse(projectRef, Seq.empty).map { it =>
      val configurations = it.configuration.map(jb.Configuration.fromString).getOrElse(Seq.empty)
      ProjectDependencyData(it.project.id, Some(it.project.build), mergeAllTestConfigurations(configurations).toSeq)
    }
    Dependencies(Nil, dependencies)
  }

  private def moduleDependencies: Seq[ModuleDependencyData] =
    forAllConfigurations(modulesIn).map { case (moduleId, configurations) =>
      ModuleDependencyData(moduleId, mapConfigurations(configurations))
    }

  private def jarDependencies: Seq[JarDependencyData] =
    forAllConfigurations(jarsIn).map { case (file, configurations) =>
      JarDependencyData(file, mapConfigurations(configurations))
    }

  private def jarsIn(configuration: sbt.Configuration): Seq[File] =
    unmanagedClasspath(configuration).map(_.data)

  private def modulesIn(configuration: sbt.Configuration): Seq[ModuleIdentifier] =
    for {
      classpathFn <- externalDependencyClasspath.toSeq
      entry       <- classpathFn(configuration)
      moduleId    <- entry.get(Keys.moduleID.key).toSeq
      artifact    <- entry.get(Keys.artifact.key).toSeq
      identifier  <- createModuleIdentifiers(moduleId, Seq(artifact))
    } yield {
      identifier
    }

  private def forAllConfigurations[T](fn: sbt.Configuration => Seq[T]): Seq[(T, Seq[jb.Configuration])] = {
    dependencyConfigurations
      .flatMap(conf => fn(conf).map(it => (it, conf)))
      .groupBy(_._1)
      .mapValues(_.unzip._2.map(c => jb.Configuration(c.name)))
      .toSeq
  }

  // We have to perform this configurations mapping because we're using externalDependencyClasspath
  // rather than libraryDependencies (to acquire transitive dependencies),  so we detect
  // module presence (in external classpath) instead of explicitly declared configurations.
  private def mapConfigurations(configurations: Seq[jb.Configuration]): Seq[jb.Configuration] = {
    val cs = mergeAllTestConfigurations(configurations)
    if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test, jb.Configuration.Runtime)) {
      Seq(jb.Configuration.Compile)
    } else if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test)) {
      Seq(jb.Configuration.Provided)
    } else {
      cs.toSeq
    }
  }

  private lazy val allTestConfigurationNames: Seq[String] = testConfigurations.map(_.name)

  private def mergeAllTestConfigurations(configurations: Seq[jb.Configuration]): Set[jb.Configuration] =
    configurations.map(c => if (allTestConfigurationNames.contains(c.name)) jb.Configuration.Test else c).toSet
}

object DependenciesExtractor extends SbtStateOps with TaskOps {
  def taskDef: Def.Initialize[Task[DependencyData]] = Def.taskDyn {

    val state = Keys.state.value
    val projectRef = Keys.thisProjectRef.value
    val options = StructureKeys.sbtStructureOpts.value
    val dependencyConfigurations = StructureKeys.dependencyConfigurations.value
    val testConfigurations = StructureKeys.testConfigurations.value
    val sourceConfigurations = StructureKeys.sourceConfigurations.value
    val buildDependencies = Keys.buildDependencies.value

    val unmanagedClasspathTask =
      sbt.Keys.unmanagedClasspath.in(projectRef)
        .forAllConfigurations(state, dependencyConfigurations)
    val externalDependencyClasspathTask =
      sbt.Keys.externalDependencyClasspath.in(projectRef)
        .forAllConfigurations(state, dependencyConfigurations)
        .result
        .map(throwExceptionIfUpdateFailed)
        .onlyIf(options.download)

    val allSourceConfigurationsTask = StructureKeys.allSourceConfigurations.in(projectRef).get(state)
    val allTestConfigurationsTask = StructureKeys.allTestConfigurations.in(projectRef).get(state)

    val classpathConfigurationTask = sbt.Keys.classpathConfiguration.in(projectRef)
      .forAllConfigurations(state, dependencyConfigurations)

   val settings = Keys.settingsData.value

    Def.task {
      (for {
        unmanagedClasspath <- unmanagedClasspathTask
        externalDependencyClasspathOpt <- externalDependencyClasspathTask
        classpathConfiguration <- classpathConfigurationTask
        allSourceConfigurations <- allSourceConfigurationsTask
        allTestConfigurations <- allTestConfigurationsTask
      } yield {

        val projectToTransitiveDependencies = getTransitiveDependenciesForProject(
          allSourceConfigurations,
          allTestConfigurations,
          classpathConfiguration,
          projectRef,
          settings,
          buildDependencies
        )
        val extractor = new DependenciesExtractor(
          projectRef,
          buildDependencies,
          unmanagedClasspath.getOrElse(_, Nil),
          externalDependencyClasspathOpt.map(it => it.getOrElse(_, Nil)),
          dependencyConfigurations,
          testConfigurations,
          sourceConfigurations,
          options.insertProjectTransitiveDependencies,
          projectToTransitiveDependencies
        )
        extractor.extract
      }).value
    }
  }

  private case class Dependency(project: ProjectRef, configuration: jb.Configuration)


  private def getTransitiveDependenciesForProject(
    allSourceConfigurations: Seq[sbt.Configuration],
    allTestConfigurations: Seq[sbt.Configuration],
    classPathConfiguration: Map[sbt.Configuration, sbt.Configuration],
    projectRef: ProjectRef,
    settings: Settings[Scope],
    buildDependencies: BuildDependencies
  ): Map[ProjectRef, Seq[jb.Configuration]] = {

    def retrieveAllTransitiveDependencies(config: sbt.Configuration): Seq[Dependency] =
      Classpaths.interSort(projectRef, config, settings, buildDependencies)
        .filter { case (dep, _) => dep != projectRef }
        .map { case (projectRef, configName) =>
          Dependency(projectRef, jb.Configuration(configName))
        }

    val configurationToDependencies = classPathConfiguration
      .map { case (selfConfig, config) =>
        (jb.Configuration(selfConfig.name), retrieveAllTransitiveDependencies(config))
      }

    val allApplicableConfigurations = (allTestConfigurations ++ allSourceConfigurations).distinct
    val removedDependenciesWithoutSources = configurationToDependencies
      .mapValues { removeDependenciesWithoutSources(_, allApplicableConfigurations) }

    val projectToConfigurations = collection.mutable.Map.empty[ProjectRef, Seq[jb.Configuration]]
    removedDependenciesWithoutSources.foreach { case (config, projects) =>
      projects.foreach { project =>
        projectToConfigurations(project) = projectToConfigurations.getOrElse(project, Seq.empty) :+ config
      }
    }

    projectToConfigurations.toMap
  }

  /**
   * The aim of this method is to remove project dependencies with configurations that do not have sources e.g. provided.
   * It needs to be done because `Classpaths#interSort` generate transitive dependencies by analyzing configurations strings (e.g. "compile->provided")
   * So for such project: <br>
   *   {{{ val root = (project in file(".")).dependsOn(proj1 % "compile->provided") }}}
   * `Classpaths#interSort` will return:
   * {{{ compile -> (proj1, "provided)
   * runtime -> (proj1, "provided)
   * test -> (proj1, "provided) }}}
   * which in practise means that we shouldn't add `proj1` as a dependency to `root`
  */
  private def removeDependenciesWithoutSources(scopeToDependencies: Seq[Dependency], allApplicableConfigs: Seq[sbt.Configuration]): Seq[ProjectRef] = {
    val groupedByProject = scopeToDependencies
      .groupBy(_.project)
      .mapValues(_.map(_.configuration.name))

    val allApplicableConfigsNames = allApplicableConfigs.map(_.name)
    groupedByProject.filter { case (_, configs) =>
      configs.exists(allApplicableConfigsNames.contains)
    }.keys.toSeq
  }

  private def throwExceptionIfUpdateFailed(result: Result[Map[sbt.Configuration,Keys.Classpath]]): Map[sbt.Configuration, Keys.Classpath] =
    result match {
      case Value(classpath) =>
        classpath
      case Inc(incomplete) =>
        val cause = Incomplete.allExceptions(incomplete).headOption
        cause.foreach(c => throw c)
        Map.empty
    }
}
