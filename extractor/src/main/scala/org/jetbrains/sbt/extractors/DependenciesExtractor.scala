package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.{DependencyData, JarDependencyData, ModuleDependencyData, ProjectDependencyData}
import org.jetbrains.sbt.{structure => jb}
import Utilities._
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */

class DependenciesExtractor(projectRef: ProjectRef,
                            buildDependencies: Option[BuildDependencies],
                            unmanagedClasspath: sbt.Configuration => Keys.Classpath,
                            externalDependencyClasspath: sbt.Configuration => Keys.Classpath,
                            dependencyConfigurations: Seq[sbt.Configuration],
                            testConfigurations: Seq[sbt.Configuration])
  extends Modules {

  private[extractors] def extract: DependencyData =
    DependencyData(projectDependencies, moduleDependencies, jarDependencies)

  private def projectDependencies: Seq[ProjectDependencyData] =
    buildDependencies.map { dep =>
      dep.classpath.getOrElse(projectRef, Seq.empty).map { it =>
        val configurations = it.configuration.map(jb.Configuration.fromString).getOrElse(Seq.empty)
        ProjectDependencyData(it.project.id, configurations)
      }
    }.getOrElse(Seq.empty)

  private def moduleDependencies: Seq[ModuleDependencyData] = {
    val moduleToConfigurations = dependencyConfigurations
      .flatMap(configuration => modulesIn(configuration).map(module => (module, configuration)))
      .groupBy(_._1)
      .mapValues(_.unzip._2)
      .toSeq

    moduleToConfigurations.flatMap { case (moduleId, configurations) =>
      createModuleIdentifiers(moduleId, moduleId.explicitArtifacts).map { id =>
        ModuleDependencyData(id, mapConfigurations(configurations.map(c => jb.Configuration(c.name))))
      }
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

  private def jarDependencies: Seq[JarDependencyData] = {
    val jarToConfigurations = dependencyConfigurations
      .flatMap(configuration => jarsIn(configuration).map(file => (file, configuration)))
      .groupBy(_._1)
      .mapValues(_.unzip._2)
      .toSeq

    jarToConfigurations.map { case (file, configurations) =>
      JarDependencyData(file, mapConfigurations(configurations.map(c => jb.Configuration(c.name))))
    }
  }

  private def jarsIn(configuration: sbt.Configuration): Seq[File] =
    unmanagedClasspath(configuration).map(_.data).filter(_.isFile)

  private def modulesIn(configuration: sbt.Configuration): Seq[ModuleID] =
      for {
        attr <- externalDependencyClasspath(configuration)
        module <- attr.get(Keys.moduleID.key)
        artifact <- attr.get(Keys.artifact.key)
      } yield module.artifacts(artifact)

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

object DependenciesExtractor extends Extractor with Configurations {
  def apply(implicit state: State, projectRef: ProjectRef, options: Options): DependencyData = {
    val buildDependencies = projectSetting(Keys.buildDependencies)

    def unmanagedClasspath(conf: sbt.Configuration) =
      projectTask(Keys.unmanagedClasspath.in(conf)).getOrElse(Seq.empty)

    def externalDependecyClasspath(conf: sbt.Configuration) =
      Project.runTask(Keys.externalDependencyClasspath.in(projectRef, conf), state) match {
        case Some((_, Value(attrs))) => attrs
        case Some((_, Inc(incomplete))) =>
          val cause = Incomplete.allExceptions(incomplete).headOption
          cause.foreach(c => throw c)
          Seq.empty
        case _ => Seq.empty
      }

    new DependenciesExtractor(projectRef,
      buildDependencies, unmanagedClasspath, externalDependecyClasspath,
      getDependencyConfigurations, getTestConfigurations).extract
  }
}
