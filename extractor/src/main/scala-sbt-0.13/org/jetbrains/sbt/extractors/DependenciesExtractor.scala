package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */

class DependenciesExtractor(projectRef: ProjectRef,
                            buildDependencies: BuildDependencies,
                            unmanagedClasspath: sbt.Configuration => Keys.Classpath,
                            externalDependencyClasspath: Option[sbt.Configuration => Keys.Classpath],
                            dependencyConfigurations: Seq[sbt.Configuration],
                            testConfigurations: Seq[sbt.Configuration])
  extends ModulesOps {

  private[extractors] def extract: DependencyData =
    DependencyData(projectDependencies, moduleDependencies, jarDependencies)

  private def projectDependencies: Seq[ProjectDependencyData] =
    buildDependencies.classpath.getOrElse(projectRef, Seq.empty).map { it =>
      val configurations = it.configuration.map(jb.Configuration.fromString).getOrElse(Seq.empty)
      ProjectDependencyData(it.project.id, configurations)
    }

  private def moduleDependencies: Seq[ModuleDependencyData] =
    forAllConfigurations(modulesIn).map { case (moduleId, configurations) =>
      ModuleDependencyData(moduleId, toSbtStructureConfigurations(configurations))
    }

  private def jarDependencies: Seq[JarDependencyData] =
    forAllConfigurations(jarsIn).map { case (file, configurations) =>
      JarDependencyData(file, toSbtStructureConfigurations(configurations))
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

  private def forAllConfigurations[T](fn: sbt.Configuration => Seq[T]): Seq[(T, Seq[sbt.Configuration])] =
    dependencyConfigurations.flatMap(conf => fn(conf).map(it => (it, conf))).groupBy(_._1).mapValues(_.unzip._2).toSeq

  private def toSbtStructureConfigurations(confs: Seq[sbt.Configuration]): Seq[jb.Configuration] =
    mapConfigurations(confs.map(c => jb.Configuration(c.name)))

  // We have to perform this configurations mapping because we're using externalDependencyClasspath
  // rather than libraryDependencies (to acquire transitive dependencies),  so we detect
  // module presence (in external classpath) instead of explicitly declared configurations.
  private def mapConfigurations(configurations: Seq[jb.Configuration]): Seq[jb.Configuration] = {
    val jbTestConfigurations = testConfigurations.map(c => jb.Configuration(c.name))
    val cs = configurations.map(c => if (jbTestConfigurations.contains(c)) jb.Configuration.Test else c).toSet

    if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test, jb.Configuration.Runtime)) {
      Seq(jb.Configuration.Compile)
    } else if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test)) {
      Seq(jb.Configuration.Provided)
    } else {
      cs.toSeq
    }
  }
}

object DependenciesExtractor extends SbtStateOps with TaskOps {
  def taskDef: Def.Initialize[Task[DependencyData]] = Def.taskDyn {

    val state = Keys.state.value
    val projectRef = Keys.thisProjectRef.value
    val buildDependencies = Keys.buildDependencies.value
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

    Def.task {
      (for {
        unmanagedClasspath <- unmanagedClasspathTask
        externalDependencyClasspathOpt <- externalDependencyClasspathTask
      } yield {
        new DependenciesExtractor(projectRef,
          buildDependencies, unmanagedClasspath.getOrElse(_, Nil),
          externalDependencyClasspathOpt.map(it => it.getOrElse(_, Nil)),
          dependencyConfigurations, testConfigurations).extract
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
