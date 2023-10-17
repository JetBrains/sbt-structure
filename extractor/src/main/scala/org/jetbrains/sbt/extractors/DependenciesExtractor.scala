package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.extractors.DependenciesExtractor.SbtConfigurationName
import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import sbt._
import sbt.jetbrains.apiAdapter._

import scala.collection.Seq
import scala.language.postfixOps

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
                            projectToConfigurations: Map[ProjectRef, Seq[SbtConfigurationName]])
  extends ModulesOps {

  private[extractors] def extract: DependencyData = {
    val projectDependencies = if (insertProjectTransitiveDependencies)
      transitiveProjectDependencies
    else
      nonTransitiveProjectDependencies
    DependencyData(projectDependencies, moduleDependencies, jarDependencies)
  }

  private def transitiveProjectDependencies: Dependencies = {
    val IDEAScopes = Seq(jb.Configuration.Compile, jb.Configuration.Runtime, jb.Configuration.Provided).map(_.name)
    val sourceConfigurationsNames = sourceConfigurations.map(_.name)

    val dependencies = projectToConfigurations.map { case (project, configurations) =>
      val transformedConfigurations = mapSbtConfigurations(configurations).map { configuration =>
        // additional mapping is required in case of situation
        // where project1 dependsOn project2 in two custom configurations: the test one and the one containing sources.
        // Without this mapping custom test configuration will be transformed to TEST in #mapConfigurations and in the scala plugin, scope
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

  private def mapSbtConfigurations(configurations: Seq[SbtConfigurationName]): Seq[jb.Configuration] =
    mapConfigurations(configurations.map(c => jb.Configuration(c.name)))

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

    val allSourceConfigurationsTask = StructureKeys.allSourceConfigurations.in(projectRef).get(state).map(_.distinct)
    val allTestConfigurationsTask = StructureKeys.allTestConfigurations.in(projectRef).get(state).map(_.distinct)

    val classpathConfigurationTask: Task[Map[sbt.Configuration, sbt.Configuration]] = sbt.Keys.classpathConfiguration.in(projectRef)
      .forAllConfigurations(state, dependencyConfigurations)

    val settings = Keys.settingsData.value

    Def.task {
      (for {
        unmanagedClasspath <- unmanagedClasspathTask
        externalDependencyClasspathOpt <- externalDependencyClasspathTask
        classPathConfiguration <- classpathConfigurationTask
        allSourceConfigurations <- allSourceConfigurationsTask
        allTestConfigurations <- allTestConfigurationsTask
      } yield {
        val allApplicableConfigurations = (allTestConfigurations ++ allSourceConfigurations).distinct.map(x => SbtConfigurationName(x.name))
        val projectToTransitiveDependencies: Map[ProjectRef, Seq[SbtConfigurationName]] =
          getTransitiveDependenciesForProject(
            projectRef,
            settings,
            buildDependencies,
            allApplicableConfigurations,
            classPathConfiguration
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

  /**
   * Represents a single dependency item returned from [[sbt.Classpaths.interSort]]<br>
   * Note that single dependency can be represented by multiple `ProjectDependency` instances<br>
   * For example in this project {{{
   *   val root = project.dependsOn(
   *     proj1 % "compile -> test",
   *     proj2 % "compile",
   *   )
   * }}}
   * "proj1" dependency for "compile" scope will be represented by these 3 items {{{
   *   Seq(ProjectDependency(proj1, compile), ProjectDependency(proj1, runtime), ProjectDependency(proj1, test))
   * }}}
   * "proj2" dependency for "compile" scope will be represented by these 1 item {{{
   *   Seq(ProjectDependency(proj2, compile))
   * }}}
   */
  private case class ProjectDependency(project: ProjectRef, configuration: SbtConfigurationName)
  private object ProjectDependency {
    def apply(tuple: (ProjectRef, String)): ProjectDependency =
      ProjectDependency(tuple._1, SbtConfigurationName(tuple._2))
  }

  /**
   * Identifies `name` field of [[sbt.librarymanagement.Configuration.name]]<br>
   * This extra type is introduced to avoid untyped String usage for a more clear data flow
   */
  case class SbtConfigurationName(name: String)

  private def getTransitiveDependenciesForProject(
    projectRef: ProjectRef,
    settings: Settings[Scope],
    buildDependencies: BuildDependencies,
    allApplicableConfigurations: Seq[SbtConfigurationName],
    classPathConfiguration: Map[sbt.Configuration, sbt.Configuration]
  ): Map[ProjectRef, Seq[SbtConfigurationName]] = {
    val configToDependencies: Map[SbtConfigurationName, Seq[ProjectDependency]] =
      classPathConfiguration.map { case (config, configInternal) =>
        val projectDependencies = retrieveTransitiveProjectDependencies(projectRef, configInternal, settings, buildDependencies)
        (SbtConfigurationName(config.name), projectDependencies)
      }

    val configToApplicableProjects: Map[SbtConfigurationName, Seq[ProjectRef]] =
      configToDependencies.mapValues(findDependentProjectsWithApplicableConfig(_, allApplicableConfigurations))

    invert(configToApplicableProjects)
  }

  //TODO: move to some utility class
  private def invert[K, V](map: Map[K, Seq[V]]): Map[V, Seq[K]] = {
    val tuples: Seq[(V, K)] = for {
      (key, values) <- map.toSeq
      value <- values
    } yield value -> key
    tuples.groupBy(_._1).mapValues(_.map(_._2))
  }

  private def retrieveTransitiveProjectDependencies(
    projectRef: ProjectRef,
    config: sbt.Configuration,
    settings: Settings[Scope],
    buildDependencies: BuildDependencies
  ): Seq[ProjectDependency] = {
    val dependenciesAll = Classpaths.interSort(projectRef, config, settings, buildDependencies)
    val dependenciesWithoutSelf = dependenciesAll.filter(_._1 != projectRef)
    dependenciesWithoutSelf.map(ProjectDependency.apply)
  }

  //TODO: improve this doc. add more examples (in addition to `proj1 % "compile->provided"` dependency add 1-2 more examples)
  /**
   * The goal of this method is to remove project dependencies with configurations that do not have sources e.g. provided.
   * It needs to be done because `Classpaths#interSort` generate transitive dependencies by analyzing configurations strings (e.g. "compile->provided")
   * So for such project: {{{
   *   val root = project.dependsOn(
   *     proj1 % "compile->provided"
   *   )
   * }}}
   * `Classpaths#interSort` will return: {{{
   *   compile -> (proj1, "provided)
   *   runtime -> (proj1, "provided)
   *   test -> (proj1, "provided)
   * }}}
   * which in practice means that we shouldn't add `proj1` as a dependency to `root`
   */
  private def findDependentProjectsWithApplicableConfig(
    dependencies: Seq[ProjectDependency],
    applicableConfigs: Seq[SbtConfigurationName]
  ): Seq[ProjectRef] = {
    val projectToConfigs: Map[ProjectRef, Seq[SbtConfigurationName]] = dependencies
      .groupBy(_.project)
      .mapValues(_.map(_.configuration))

    val projectToConfigsWithAtLeastOneApplicableConfig =
      projectToConfigs.filter { case (_, configs) =>
        configs.exists(applicableConfigs.contains)
      }

    projectToConfigsWithAtLeastOneApplicableConfig.keys.toSeq
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
