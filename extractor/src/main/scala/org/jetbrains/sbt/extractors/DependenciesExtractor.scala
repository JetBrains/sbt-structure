package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.extractors.DependenciesExtractor.{ProductionType, ProjectType, TestType}
import org.jetbrains.sbt.structure._
import sbt.{Configuration => SbtConfiguration, _}
import sbt.jetbrains.apiAdapter._

import scala.collection.{Seq, mutable}
import scala.language.postfixOps

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */

class DependenciesExtractor(unmanagedClasspath: SbtConfiguration => Keys.Classpath,
                            externalDependencyClasspath: Option[SbtConfiguration => Keys.Classpath],
                            dependencyConfigurations: Seq[SbtConfiguration],
                            testConfigurations: Seq[SbtConfiguration],
                            sourceConfigurations: Seq[SbtConfiguration],
                            separateProdTestSources: Boolean,
                            projectToConfigurations: Map[ProjectType, Seq[Configuration]])
  extends ModulesOps {

  private lazy val testConfigurationNames = testConfigurations.map(_.name)
  private lazy val IDEAScopes = Seq(Configuration.Compile, Configuration.Runtime, Configuration.Provided, Configuration.Test).map(_.name)
  private lazy val sourceConfigurationsNames = sourceConfigurations.map(_.name)

  private[extractors] def extract: DependencyData = {
    val projectDependencies =
      if (separateProdTestSources) separatedSourcesProjectDependencies
      else transitiveProjectDependencies
    DependencyData(projectDependencies, moduleDependencies, jarDependencies)
  }

  private def transitiveProjectDependencies: Dependencies[ProjectDependencyData] = {
    val dependencies = projectToConfigurations.map { case(ProjectType(project), configurations) =>
      val transformedConfigurations = mapConfigurations(configurations).map(mapCustomSourceConfigurationIfApplicable)
      ProjectDependencyData(project.id, Some(project.build), transformedConfigurations)
    }.toSeq
    Dependencies(dependencies, Seq.empty)
  }

  private def mapToProjectNameWithSourceTypeAppended(projectType: ProjectType): String = {
    val projectName = projectType.project.project
    projectType match {
      case ProductionType(_) => s"$projectName:main"
      case TestType(_) => s"$projectName:test"
    }
  }

  private def separatedSourcesProjectDependencies: Dependencies[ProjectDependencyData] = {
    processDependencies(projectToConfigurations.toSeq) { case (projectType @ ProjectType(project), configs) =>
      val projectName = mapToProjectNameWithSourceTypeAppended(projectType)
      ProjectDependencyData(projectName, Option(project.build), configs)
    }
  }

  private def moduleDependencies: Dependencies[ModuleDependencyData] = {
    val allModuleDependencies = forAllConfigurations(modulesIn)
    if (separateProdTestSources) {
      processDependencies(allModuleDependencies) { case(moduleId, configs) =>
        ModuleDependencyData(moduleId, configs)
      }
    } else {
      val dependencies = allModuleDependencies.map { case(moduleId, configs) =>
        ModuleDependencyData(moduleId, mapConfigurations(configs))
      }
      Dependencies(dependencies, Seq.empty)
    }
  }

  private def jarDependencies: Dependencies[JarDependencyData] = {
    val allJarDependencies = forAllConfigurations(jarsIn)
    if (separateProdTestSources) {
      processDependencies(allJarDependencies) { case (file, configs) =>
        JarDependencyData(file, configs)
      }
    } else {
      val dependencies = allJarDependencies.map { case (file, configs) =>
        JarDependencyData(file, mapConfigurations(configs))
      }
      Dependencies(dependencies, Seq.empty)
    }
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
    val result = mutable.LinkedHashMap.empty[T, Seq[Configuration]]
    dependencyConfigurations.flatMap(conf => fn(conf).map(it => (it, Configuration(conf.name)))).foreach {
      case (t, conf) =>
        val confs = result.getOrElse(t, Seq.empty)
        result(t) = confs :+ conf
    }
    result.toSeq
  }

  /**
   *  Configurations passed in a parameter indicate in what configurations some dependency (project, module, jar) is present. Based on that
   *  we can infer where (prod/test modules) and in what scope this dependency should be added.
   */
  private def splitConfigurationsToDifferentSourceSets(configurations: Seq[Configuration]): Dependencies[Configuration] = {
    val cs = mergeAllTestConfigurations(configurations)
    val (prodConfigs, testConfigs) = {
      if (Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime).forall(cs.contains)) { // compile configuration
        (Seq(Configuration.Compile), Seq(Configuration.Compile))
      } else {
        Seq(
          // 1. The downside of this logic is that if cs contains only some custom source configuration (not one that is available in sbt by default),
          // then prodConfigs will be empty. It is fixed in #updateProductionConfigs.
          // Mapping custom source configurations to Compile, couldn't be done at the same place as
          // #mergeAllTestConfigurations, because then Compile would be converted into Provided and it is not the purpose.
          // 2. These 3 conditions are also suitable for -internal configurations because when e.g. compile-internal configuration
          // is used cs contains only compile configuration and this will cause the dependency to be added to the production module
          // with the provided scope
          (cs.contains(Configuration.Test), Nil, Seq(Configuration.Compile)),
          (cs.contains(Configuration.Compile), Seq(Configuration.Provided), Nil),
          (cs.contains(Configuration.Runtime), Seq(Configuration.Runtime), Nil)
        ).foldLeft((Seq.empty[Configuration], Seq.empty[Configuration])) { case((productionSoFar, testSoFar), (condition, productionUpdate, testUpdate)) =>
          if (condition) (productionSoFar ++ productionUpdate, testSoFar ++ testUpdate)
          else (productionSoFar, testSoFar)
        }
      }
    }
    val updatedProdConfigs = updateProductionConfigs(prodConfigs, cs)
    Dependencies(updatedProdConfigs, testConfigs)
  }

  private def updateProductionConfigs(prodConfigs: Seq[Configuration], allConfigs: Set[Configuration]): Seq[Configuration] = {
    val containsProvidedAndRuntime = Seq(Configuration.Provided, Configuration.Runtime).forall(prodConfigs.contains)
    val isCustomSourceConfigPresent = allConfigs.map(_.name).toSeq.intersect(sourceConfigurationsNames).nonEmpty

    val shouldBeCompileScope = containsProvidedAndRuntime || (prodConfigs.isEmpty && isCustomSourceConfigPresent)
    if (shouldBeCompileScope) {
      Seq(Configuration.Compile)
    } else {
      prodConfigs
    }
  }

  private def processDependencies[D, F](
    dependencies: Seq[(D, Seq[Configuration])]
  )(mapToTargetType: ((D, Seq[Configuration])) => F): Dependencies[F] = {
    val productionDependencies = mutable.Map.empty[D, Seq[Configuration]]
    val testDependencies = mutable.Map.empty[D, Seq[Configuration]]

    def updateDependenciesInProductionAndTest(project: D, configsForProduction: Seq[Configuration], configsForTest: Seq[Configuration]): Unit = {
      Seq((productionDependencies, configsForProduction), (testDependencies, configsForTest))
        .filterNot(_._2.isEmpty)
        .foreach { case(dependencies, configs) =>
          val existingConfigurations = dependencies.getOrElse(project, Seq.empty)
          dependencies.update(project, existingConfigurations ++ configs)
        }
    }

    dependencies.foreach { case(dependency, configurations) =>
      val cs = splitConfigurationsToDifferentSourceSets(configurations)
      updateDependenciesInProductionAndTest(dependency, cs.forProduction, cs.forTest)
    }
    Dependencies(productionDependencies.toSeq.map(mapToTargetType), testDependencies.toSeq.map(mapToTargetType))
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
  private def mapCustomSourceConfigurationIfApplicable(configuration: Configuration): Configuration = {
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
    val settings = Keys.settingsData.value
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

    def getProjectToConfigurations(key: SettingKey[Seq[SbtConfiguration]]) =
      key.forAllProjects(state, allAcceptedProjects).toMap.mapValues(_.map(_.name)).withDefaultValue(Seq.empty)

    val projectToSourceConfigurations = getProjectToConfigurations(StructureKeys.sourceConfigurations)
    val projectToTestConfigurations = getProjectToConfigurations(StructureKeys.testConfigurations)

    val projectToConfigurations = allAcceptedProjects.map { proj =>
      proj -> ProjectConfigurations(projectToSourceConfigurations(proj), projectToTestConfigurations(proj))
    }.toMap

    Def.task {
      (for {
        unmanagedClasspath <- unmanagedClasspathTask
        externalDependencyClasspathOpt <- externalDependencyClasspathTask
        classpathConfiguration <- classpathConfigurationTask
      } yield {
        val projectDependencies =
          if (options.separateProdAndTestSources) {
            getTransitiveDependenciesForProjectProdTestSources(
              projectRef,
              projectToConfigurations,
              classpathConfiguration,
              settings,
              buildDependencies
            )
          } else {
            getTransitiveDependenciesForProject(
              projectRef,
              projectToConfigurations,
              classpathConfiguration,
              settings,
              buildDependencies
            )
          }

        val extractor = new DependenciesExtractor(
          unmanagedClasspath.getOrElse(_, Nil),
          externalDependencyClasspathOpt.map(it => it.getOrElse(_, Nil)),
          dependencyConfigurations,
          testConfigurations,
          sourceConfigurations,
          options.separateProdAndTestSources,
          projectDependencies
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
    projectToConfigurations: Map[ProjectRef, ProjectConfigurations],
    classPathConfiguration: Map[SbtConfiguration, SbtConfiguration],
    settings: Settings[Scope],
    buildDependencies: BuildDependencies
  ): Map[ProjectType, Seq[Configuration]] = {
    val dependencyToConfigurations = retrieveTransitiveProjectToConfigsDependencies(
      projectRef,
      classPathConfiguration,
      settings,
      buildDependencies,
      projectToConfigurations
    )
    mapDependenciesToProjectType(dependencyToConfigurations) { projectDependency => ProductionType(projectDependency.project) }
  }

  private def mapDependenciesToProjectType(
    dependencyToConfigurations: Map[ProjectDependency, Seq[Configuration]]
  )(projectDependencyMapping: ProjectDependency => ProjectType): Map[ProjectType, Seq[Configuration]] =
    dependencyToConfigurations.foldLeft(Map.empty[ProjectType, Seq[Configuration]]) { case (acc, (projectDependency, configs)) =>
      val projectType = projectDependencyMapping(projectDependency)
      val existingConfigurations = acc.getOrElse(projectType, Seq.empty)
      acc.updated(projectType, (existingConfigurations ++ configs).distinct)
    }

  private case class ProjectConfigurations(source: Seq[String], test: Seq[String])

  private[extractors] sealed abstract class ProjectType(val project: ProjectRef)
  private[extractors] case class ProductionType(override val project: ProjectRef) extends ProjectType(project)
  private[extractors] case class TestType(override val project: ProjectRef) extends ProjectType(project)

  object ProjectType {
    def unapply(projectType: ProjectType): Option[ProjectRef] = Some(projectType.project)
  }

  private def retrieveTransitiveProjectToConfigsDependencies(
    projectRef: ProjectRef,
    classPathConfiguration: Map[SbtConfiguration, SbtConfiguration],
    settings: Settings[Scope],
    buildDependencies: BuildDependencies,
    projectToConfigurations: Map[ProjectRef, ProjectConfigurations]
  ): Map[ProjectDependency, Seq[Configuration]] = {
    val configToDependencies = classPathConfiguration.map { case (selfConfig, config) =>
        val projectDependencies = retrieveTransitiveProjectDependencies(projectRef, config, settings, buildDependencies, projectToConfigurations)
        (Configuration(selfConfig.name), projectDependencies)
      }
    invert(configToDependencies)
  }

  private def getTransitiveDependenciesForProjectProdTestSources(
    projectRef: ProjectRef,
    projectToConfigurations: Map[ProjectRef, ProjectConfigurations],
    classPathConfiguration: Map[SbtConfiguration, SbtConfiguration],
    settings: Settings[Scope],
    buildDependencies: BuildDependencies
  ): Map[ProjectType, Seq[Configuration]] = {
    val dependencyToConfigurations = retrieveTransitiveProjectToConfigsDependencies(
      projectRef,
      classPathConfiguration,
      settings,
      buildDependencies,
      projectToConfigurations
    )
    val keysMappedToProjectType = mapDependenciesToProjectType(dependencyToConfigurations) { case ProjectDependency(project, configuration) =>
      projectToConfigurations.get(project) match {
        case Some(projectConfigurations) if projectConfigurations.test.contains(configuration) =>
          TestType(project)
        case _ =>
          ProductionType(project)
      }
    }

    keysMappedToProjectType + (ProductionType(projectRef) -> Seq(Configuration.Test))
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
    buildDependencies: BuildDependencies,
    projectToConfigurations: Map[ProjectRef, ProjectConfigurations]
  ): Seq[ProjectDependency] = {
    val allDependencies = Classpaths.interSort(projectRef, config, settings, buildDependencies)
    val dependenciesWithoutProjectItself = allDependencies
      // note: removing dependencies to the origin project itself (when prod/test sources are separated prod part is always added to the test part in #getTransitiveDependenciesForProjectProdTestSources)
      // and projects with configurations that do not have sources e.g. provided
      .filter { case(project, config) => project != projectRef && isProjectDependencyInSourceConfiguration(project, config, projectToConfigurations) }

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
  private def isProjectDependencyInSourceConfiguration(
    project: ProjectRef,
    configuration: String,
    projectToConfigurations: Map[ProjectRef, ProjectConfigurations]
  ): Boolean =
    projectToConfigurations.get(project)
      .fold(Seq.empty[String])(t => t.source ++ t.test)
      .contains(configuration)

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
