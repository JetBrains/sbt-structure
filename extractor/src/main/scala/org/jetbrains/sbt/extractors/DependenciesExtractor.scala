package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import sbt.{Configuration => SbtConfiguration, _}
import sbt.jetbrains.apiAdapter._

import scala.collection.Seq

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */

class DependenciesExtractor(projectRef: ProjectRef,
                            buildDependencies: BuildDependencies,
                            unmanagedClasspath: SbtConfiguration => Keys.Classpath,
                            externalDependencyClasspath: Option[SbtConfiguration => Keys.Classpath],
                            dependencyConfigurations: Seq[SbtConfiguration],
                            testConfigurations: Seq[SbtConfiguration],
                            sourceConfigurations: Seq[SbtConfiguration],
                            insertProjectTransitiveDependencies: Boolean,
                            projectToConfigurations: Map[ProjectRef, Seq[Configuration]])
  extends ModulesOps {

  private lazy val testConfigurationNames = testConfigurations.map(_.name)
  private lazy val IDEAScopes = Seq(Configuration.Compile, Configuration.Runtime, Configuration.Provided, Configuration.Test).map(_.name)
  private lazy val sourceConfigurationsNames = sourceConfigurations.map(_.name)

  private[extractors] def extract: DependencyData = {
    val projectDependencies =
      if (insertProjectTransitiveDependencies) transitiveProjectDependencies
      else nonTransitiveProjectDependencies
    DependencyData(projectDependencies, moduleDependencies, jarDependencies)
  }

  private def transitiveProjectDependencies: Seq[ProjectDependencyData] = {
    val dependencies = projectToConfigurations.map { case (project, configurations) =>
      val transformedConfigurations = mapConfigurations(configurations).map(mapCustomSourceConfigurationToCompileIfApplicable)
      ProjectDependencyData(project.id, Some(project.build), transformedConfigurations)
    }.toSeq
    dependencies
  }

  private def nonTransitiveProjectDependencies: Seq[ProjectDependencyData] =
    buildDependencies.classpath.getOrElse(projectRef, Seq.empty).map { it =>
      val configurations = it.configuration.map(Configuration.fromString).getOrElse(Seq.empty)
      ProjectDependencyData(it.project.id, Some(it.project.build), configurations)
    }

  private def moduleDependencies: Seq[ModuleDependencyData] =
    forAllConfigurations(modulesIn).map { case (moduleId, configurations) =>
      ModuleDependencyData(moduleId, mapConfigurations(configurations))
    }

  private def jarDependencies: Seq[JarDependencyData] =
    forAllConfigurations(jarsIn).map { case (file, configurations) =>
      JarDependencyData(file, mapConfigurations(configurations))
    }

  private def jarsIn(configuration: SbtConfiguration): Seq[File] =
    unmanagedClasspath(configuration).map(_.data)

  private def modulesIn(configuration: SbtConfiguration): Seq[ModuleIdentifier] =
    for {
      classpathFn <- externalDependencyClasspath.toSeq
      entry       <- classpathFn(configuration)
      moduleId    <- entry.get(Keys.moduleID.key).toSeq
      artifact    <- entry.get(Keys.artifact.key).toSeq
      identifier  <- createModuleIdentifiers(moduleId, Seq(artifact))
    } yield {
      identifier
    }

  private def forAllConfigurations[T](fn: SbtConfiguration => Seq[T]): Seq[(T, Seq[Configuration])] = {
    dependencyConfigurations
      .flatMap(conf => fn(conf).map(it => (it, conf)))
      .groupBy(_._1)
      .mapValues(_.unzip._2.map(c => Configuration(c.name)))
      .toSeq
  }

  // We have to perform this configurations mapping because we're using externalDependencyClasspath
  // rather than libraryDependencies (to acquire transitive dependencies),  so we detect
  // module presence (in external classpath) instead of explicitly declared configurations.
  private def mapConfigurations(configurations: Seq[Configuration]): Seq[Configuration] = {
    val cs = mergeAllTestConfigurations(configurations)
    if (cs == Set(Configuration.Compile, Configuration.Test, Configuration.Runtime)) {
      Seq(Configuration.Compile)
    } else if (cs == Set(Configuration.Compile, Configuration.Test)) {
      Seq(Configuration.Provided)
    } else {
      cs.toSeq
    }
  }

  /**
   * This mapping is required in case of situation
   * where some project depends on another project in two custom configurations: the test one and the one containing sources.
   * For example in this project {{{
   * val CustomTest = config("customtest").extend(Test)
   * val CustomCompile = config("customcompile").extend(Compile)
   *
   * val dummy = (project in file("dummy"))
   * val utils = (project in file("utils"))
   *  .configs(CustomTest, CustomCompile)
   *  .settings(
   *    inConfig(CustomCompile)(Defaults.configSettings),
   *    inConfig(CustomTest)(Defaults.configSettings))
   *  .dependsOn(dummy % "customtest;customcompile")
   * }}}
   * Without performing the action of this method, `customtest` configuration will be mapped to `Configuration.Test` in
   * [[org.jetbrains.sbt.extractors.DependenciesExtractor.mapConfigurations]] and then in the scala plugin, scope for this dependency
   * would be calculated to `TEST` (in [[org.jetbrains.sbt.project.SbtProjectResolver.scopeFor]]) which would not be truth,
   * because now the scope for `dummy` dependency should be `COMPILE`.
   *
   * Check behaviour of this logic when SCL-18284 will be fixed
   */
  private def mapCustomSourceConfigurationToCompileIfApplicable(configuration: Configuration): Configuration = {
    val matchIDEAScopes = IDEAScopes.contains(configuration.name)
    val isSourceConfiguration = sourceConfigurationsNames.contains(configuration.name)
    if (!matchIDEAScopes && isSourceConfiguration) {
      Configuration.Compile
    } else {
      configuration
    }
  }

  private def mergeAllTestConfigurations(configurations: Seq[Configuration]): Set[Configuration] =
    configurations.map(c => if (testConfigurationNames.contains(c.name)) Configuration.Test else c).toSet
}

object DependenciesExtractor extends SbtStateOps with TaskOps {
  def taskDef: Def.Initialize[Task[DependencyData]] = Def.taskDyn {

    val state = Keys.state.value
    val projectRef = Keys.thisProjectRef.value
    val options = StructureKeys.sbtStructureOpts.value
    //example: Seq(compile, runtime, test, provided, optional)
    val dependencyConfigurations = StructureKeys.dependencyConfigurations.value
    //example: Seq(test, it)
    val testConfigurations = StructureKeys.testConfigurations.value
    //example: Seq(compile, runtime)
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

    //example: Map(compile -> compile-internal, test -> test-internal,  runtime -> runtime-internal)
    val classpathConfigurationTask = sbt.Keys.classpathConfiguration.in(projectRef)
      .forAllConfigurations(state, dependencyConfigurations)

    val allAcceptedProjects = StructureKeys.acceptedProjects.value
    val allConfigurationsWithSourceOfAllProjects = (StructureKeys.allConfigurationsWithSource
      .forAllProjects(state, allAcceptedProjects)
      // From what I checked adding a Compile configuration explicitly is not needed here but this is how it is done in
      // org.jetbrains.sbt.extractors.UtilityTasks.sourceConfigurations so probably for some cases it is required
      .flatMap(_._2) ++ Seq(sbt.Compile)).distinct

    val settings = Keys.settingsData.value

    Def.task {
      (for {
        unmanagedClasspath <- unmanagedClasspathTask
        externalDependencyClasspathOpt <- externalDependencyClasspathTask
        classpathConfiguration <- classpathConfigurationTask
      } yield {

        val projectToTransitiveDependencies = if (options.insertProjectTransitiveDependencies) {
          getTransitiveDependenciesForProject(
            projectRef,
            allConfigurationsWithSourceOfAllProjects,
            classpathConfiguration,
            settings,
            buildDependencies
          )
        } else
          Map.empty[ProjectRef, Seq[Configuration]]

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

  /**
   * Represents a single dependency item returned from [[sbt.Classpaths.interSort]]<br>
   * Note that single dependency can be represented by multiple `ProjectDependency` instances<br>
   * For example in this project {{{
   *   val root = project.dependsOn(
   *     proj1 % "compile -> test",
   *     proj2 % "compile",
   *   )
   * }}}
   * In scope compile "proj1" dependency will be represented by these 3 items {{{
   *   Seq(ProjectDependency(proj1, compile), ProjectDependency(proj1, runtime), ProjectDependency(proj1, test))
   * }}}
   * On the other hand "proj2" dependency in "compile" scope will be represented by only 1 item {{{
   *   Seq(ProjectDependency(proj2, compile))
   * }}}
   *
   * @see [[org.jetbrains.sbt.extractors.DependenciesExtractor.retrieveTransitiveProjectDependencies]]
   */
  private case class ProjectDependency(project: ProjectRef, configuration: String)
  private object ProjectDependency {
    def apply(tuple: (ProjectRef, String)): ProjectDependency =
      ProjectDependency(tuple._1, tuple._2)
  }

  private def getTransitiveDependenciesForProject(
    projectRef: ProjectRef,
    allConfigurationsWithSourceOfAllProjects: Seq[SbtConfiguration],
    classPathConfiguration: Map[SbtConfiguration, SbtConfiguration],
    settings: Settings[Scope],
    buildDependencies: BuildDependencies
  ): Map[ProjectRef, Seq[Configuration]] = {
    val configToDependencies: Map[Configuration, Seq[ProjectDependency]] =
      classPathConfiguration.map { case (selfConfig, config) =>
        val projectDependencies = retrieveTransitiveProjectDependencies(projectRef, config, settings, buildDependencies)
        (Configuration(selfConfig.name), projectDependencies)
      }

    val allConfigurationsWithSourceOfAllProjectsNames = allConfigurationsWithSourceOfAllProjects.map(_.name)
    val configToProjects: Map[Configuration, Seq[ProjectRef]] = configToDependencies.mapValues {
        keepProjectsWithAtLeastOneSourceConfig(_, allConfigurationsWithSourceOfAllProjectsNames)
      }

    invert(configToProjects)
  }

  /**
   * Retrieving project transitive dependencies is done using [[sbt.Classpaths.interSort]] from sbt.
   * It finds all transitive dependencies by static analysis of the configuration strings (like e.g. "compile" or "compile->test").
   * The dependencies returned from [[sbt.Classpaths.interSort]] are increased by the dependencies with configurations
   * that a target configuration inherits from.
   * So for such project: {{{
   *   val root = project.dependsOn(
   *     proj1 % "compile->test"
   *   )
   * }}}
   * [[sbt.Classpaths.interSort]] for `compile` scope in project `root` will return: {{{
   *   compile -> (proj1, "compile"), (proj1, "test"), (proj1, "runtime")
   * }}}
   * It will return such a result because `test` configuration extends `runtime` and `runtime` configuration extends `compile`
   */
  private def retrieveTransitiveProjectDependencies(
    projectRef: ProjectRef,
    config: sbt.Configuration,
    settings: Settings[Scope],
    buildDependencies: BuildDependencies
  ): Seq[ProjectDependency] = {
    val allDependencies = Classpaths.interSort(projectRef, config, settings, buildDependencies)
    // note: when production and test sources will be separated removing all dependencies
    // with origin project itself, should be done more carefully because it will be required to put projectRef production sources in test sources
    val dependenciesWithoutProjectItself = allDependencies.filter(_._1 != projectRef)
    dependenciesWithoutProjectItself.map(ProjectDependency.apply)
  }

  /**
   * The goal of this method is to remove project dependencies with configurations that do not have sources e.g. provided.
   * It needs to be done because [[sbt.Classpaths.interSort]] generate transitive dependencies by analyzing configurations strings (e.g. "compile->provided")
   * and not every configuration string means that this dependency should actually be added.
   * So for such project: {{{
   *   val root = project.dependsOn(
   *     proj1 % "compile->provided",
   *     proj2 % "compile"
   *   )
   * }}}
   * [[sbt.Classpaths.interSort]] for `compile` scope in project `root` will return: {{{
   *   compile -> (proj1, "provided"), (proj2, "compile")
   * }}}
   * which in practice means that we only have to add `proj2` as a dependency to `root` and `proj1` dependency shouldn't be taken into account.
   */
  private def keepProjectsWithAtLeastOneSourceConfig(
    dependencies: Seq[ProjectDependency],
    allConfigurationsWithSourceOfAllProjectsNames: Seq[String]
  ): Seq[ProjectRef] = {
    val projectToConfigs = dependencies
      .groupBy(_.project)
      .mapValues(_.map(_.configuration))

    // note: when production and test sources will be separated we shouldn't just check
    // whether there is at least one source configuration per project (it is a very big simplification but sufficient for now).
    // For separating production and test sources an analysis should be done to determine what exactly are the configurations of the dependent project and
    // from this we should conclude whether we should add production or test part of dependent project to the owner of the dependency.
    // There is still a question of where (in production or test sources of the owner of the dependency) to put production/test part of dependent project,
    // but it should probably be done in a different place.
    val projectToConfigsWithAtLeastOneSourceConfig =
      projectToConfigs.filter { case (_, dependencies) =>
        dependencies.exists(allConfigurationsWithSourceOfAllProjectsNames.contains)
      }

    projectToConfigsWithAtLeastOneSourceConfig.keys.toSeq
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
