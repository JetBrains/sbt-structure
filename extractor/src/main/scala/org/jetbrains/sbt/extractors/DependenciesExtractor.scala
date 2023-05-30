package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import sbt._
import sbt.jetbrains.apiAdapter._

import scala.collection.{Seq, mutable}

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */

class DependenciesExtractor(projectRef: ProjectRef,
                            unmanagedClasspath: sbt.Configuration => Keys.Classpath,
                            externalDependencyClasspath: Option[sbt.Configuration => Keys.Classpath],
                            dependencyConfigurations: Seq[sbt.Configuration],
                            testConfigurations: Seq[sbt.Configuration],
                            internalDependencyClasspath: sbt.Configuration => Keys.Classpath,
                            exportedClasspathToProjectMapping: Map[File, ProjectRef])
  extends ModulesOps {

  private[extractors] def extract: DependencyData ={
    val projectDependencies = processDependenciesMap(forAllConfigurations(projectsIn)) { case ((project, uri), configs) =>
      ProjectDependencyData(project, uri, configs)
    }
    DependencyData(projectDependencies, moduleDependencies, jarDependencies)
  }

  private def processDependenciesMap[D, F](dependenciesList: Seq[(D, Seq[jb.Configuration])])
                                          (mapToTargetType: ((D, Seq[jb.Configuration])) => F): Dependencies[F] = {
    val productionDependencies = mutable.Map.empty[D, Seq[jb.Configuration]]
    val testDependencies = mutable.Map.empty[D, Seq[jb.Configuration]]

    def updateDependenciesInProductionAndTest(project: D, configsForProduction: Seq[jb.Configuration], configsForTest: Seq[jb.Configuration]): Unit = {
      Seq((productionDependencies, configsForProduction), (testDependencies, configsForTest))
        .filterNot(_._2.isEmpty)
        .foreach { case (dependencies, configs) =>
          val existingConfigurations = dependencies.getOrElse(project, Seq.empty)
          dependencies.update(project, existingConfigurations ++ configs)
        }
    }

    dependenciesList.foreach { case (dependency, configurations) =>
      val cs = mapProjectDependenciesConfigurations(configurations)
      updateDependenciesInProductionAndTest(dependency, cs.forProductionSources, cs.forTestSources)
    }
    Dependencies(testDependencies.toSeq.map(mapToTargetType), productionDependencies.toSeq.map(mapToTargetType))
  }

  // We have to perform this configurations mapping because we're using sbt keys, which give
  // us only information which dependencies are visible in what configurations instead of explicitly declared configurations.
  private def mapProjectDependenciesConfigurations(configurations: Seq[jb.Configuration]): Dependencies[jb.Configuration] = {
    val cs = swapAllDerivativesOfTestConfigurations(configurations)
    // TODO: after splitting production and test sources all test configurations should be changed to Compile (sbt does not distinguish between test compile & runtime)
    val resultConfigurations: (Seq[jb.Configuration], Seq[jb.Configuration]) = {
      if (Seq(jb.Configuration.Compile, jb.Configuration.Test, jb.Configuration.Runtime).forall(cs.contains)) { // compile configuration
        (Seq(jb.Configuration.Compile), Seq(jb.Configuration.Compile))
      } else if (Seq(jb.Configuration.Compile, jb.Configuration.Test).forall(cs.contains)) { // provided configuration
        (Seq(jb.Configuration.Provided), Seq(jb.Configuration.Provided))
      } else if (Seq(jb.Configuration.Test, jb.Configuration.Runtime).forall(cs.contains)) { // runtime configuration
        (Seq(jb.Configuration.Runtime), Seq(jb.Configuration.Runtime))
      } else {
        if (!cs.exists(Seq(jb.Configuration.Compile, jb.Configuration.Runtime, jb.Configuration.Test).contains(_))) {
          (Seq(jb.Configuration.Compile), Seq(jb.Configuration.Compile))
        } else {
          Seq(
            (cs.contains(jb.Configuration.Test), (Nil, Seq(jb.Configuration.Test))),
            (cs.contains(jb.Configuration.Compile), (Seq(jb.Configuration.Provided), Nil)),
            (cs.contains(jb.Configuration.Runtime), (Seq(jb.Configuration.Runtime), Nil))
          ).foldLeft((Seq.empty[jb.Configuration], Seq.empty[jb.Configuration])) { (acc, cur) =>
            val (condition, (productionConfigs, testConfigs)) = cur
            if (condition) (acc._1 ++ productionConfigs, acc._2 ++ testConfigs)
            else acc
          }
        }
      }
    }
    Dependencies(resultConfigurations._2, resultConfigurations._1)
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

  private def projectsIn(configuration: sbt.Configuration): Seq[(String, Some[URI])] =
    for {
      entry <- internalDependencyClasspath(configuration)
      project <- exportedClasspathToProjectMapping.get(entry.data)
      if !(configuration.name == jb.Configuration.Runtime.name && project == projectRef)
    } yield {
      (project.project, Some(project.build))
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
    val cs = swapAllDerivativesOfTestConfigurations(configurations)
    if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test, jb.Configuration.Runtime)) {
      Seq(jb.Configuration.Compile)
    } else if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test)) {
      Seq(jb.Configuration.Provided)
    } else {
      cs.toSeq
    }
  }

  private def swapAllDerivativesOfTestConfigurations(configurations: Seq[jb.Configuration]): Set[jb.Configuration] = {
    val jbTestConfigurations = testConfigurations.map(c => jb.Configuration(c.name))
    configurations.map(c => if (jbTestConfigurations.contains(c)) jb.Configuration.Test else c).toSet
  }
}

object DependenciesExtractor extends SbtStateOps with TaskOps {
  def taskDef: Def.Initialize[Task[DependencyData]] = Def.taskDyn {

    val state = Keys.state.value
    val projectRef = Keys.thisProjectRef.value
    val options = StructureKeys.sbtStructureOpts.value
    val dependencyConfigurations = StructureKeys.dependencyConfigurations.value
    val testConfigurations = StructureKeys.testConfigurations.value

    val unmanagedClasspathTask =
      sbt.Keys.unmanagedClasspath.in(projectRef)
        .forAllConfigurations(state, dependencyConfigurations)
    val externalDependencyClasspathTask =
      sbt.Keys.externalDependencyClasspath.in(projectRef)
        .forAllConfigurations(state, dependencyConfigurations)
        .result
        .map(throwExceptionIfUpdateFailed)
        .onlyIf(options.download)


    val isAndroidProject = StructureKeys.extractAndroid.value.nonEmpty

    val internalDependencyClasspathTask = sbt.Keys.internalDependencyClasspath.in(projectRef)
        .forAllConfigurations(state, dependencyConfigurations, isAndroidProject)
        .result
        .map(throwExceptionIfUpdateFailed)

    val configurationsForExportedClasspath = Seq(sbt.Compile, sbt.Test, sbt.IntegrationTest)
    val acceptedProjects = StructureKeys.acceptedProjects.value
    val exportedProductsTask = sbt.Keys.exportedProductsNoTracking
      .forAllProjectsAndConfigurations(state, configurationsForExportedClasspath, acceptedProjects, isAndroidProject)

    Def.task {
      (for {
        unmanagedClasspath <- unmanagedClasspathTask
        externalDependencyClasspathOpt <- externalDependencyClasspathTask
        internalDependencyClasspath <- internalDependencyClasspathTask
        exportedClasspath <- exportedProductsTask
      } yield {
        val exportedClasspathToProjectMapping = exportedClasspath.flatMap { case (classpath, project) =>
          classpath.map { attributedFile => (attributedFile.data, project) }
        }
        new DependenciesExtractor(projectRef,
          unmanagedClasspath.getOrElse(_, Nil),
          externalDependencyClasspathOpt.map(it => it.getOrElse(_, Nil)),
          dependencyConfigurations, testConfigurations,
          internalDependencyClasspath.getOrElse(_, Nil),
          exportedClasspathToProjectMapping).extract
      }).value
    }
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
