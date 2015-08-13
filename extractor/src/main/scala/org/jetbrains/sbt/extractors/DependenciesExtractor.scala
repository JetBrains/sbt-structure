package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.extractors.Extractor.Options
import org.jetbrains.sbt.structure.{DependencyData, JarDependencyData, ModuleDependencyData, ProjectDependencyData}
import org.jetbrains.sbt.{structure => jb}
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class DependenciesExtractor(projectRef: ProjectRef) extends ModulesExtractor {
  override type Data = DependencyData

  implicit val projectRefImplicit = projectRef

  override def extract(implicit state: State, options: Options): Option[DependencyData] =
    Some(DependencyData(projectDependencies, moduleDependencies, jarDependencies))

  private def projectDependencies(implicit state: State): Seq[ProjectDependencyData] =
    projectSetting(Keys.buildDependencies).map { dep =>
      dep.classpath.getOrElse(projectRef, Seq.empty).map { it =>
        val configurations = it.configuration.map(jb.Configuration.fromString).getOrElse(Seq.empty)
        ProjectDependencyData(it.project.project, configurations)
      }
    }.getOrElse(Seq.empty)

  private def moduleDependencies(implicit state: State, options: Options): Seq[ModuleDependencyData] =
    if (options.download) getModuleDependencies else Seq.empty

  private def getModuleDependencies(implicit state: State): Seq[ModuleDependencyData] = {
    val moduleToConfigurations = DependencyConfigurations
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

  private def jarDependencies(implicit state: State): Seq[JarDependencyData] = {
    val jarToConfigurations = DependencyConfigurations
      .flatMap(configuration => jarsIn(configuration).map(file => (file, configuration)))
      .groupBy(_._1)
      .mapValues(_.unzip._2)
      .toSeq

    jarToConfigurations.map { case (file, configurations) =>
      JarDependencyData(file, mapConfigurations(configurations.map(c => jb.Configuration(c.name))))
    }
  }

  private def jarsIn(configuration: sbt.Configuration)(implicit state: State): Seq[File] =
    projectTask(Keys.unmanagedClasspath.in(configuration)).map(_.map(_.data)).getOrElse(Seq.empty).filter(_.isFile)

  private def modulesIn(configuration: sbt.Configuration)(implicit state: State): Seq[ModuleID] = {
    Project.runTask(Keys.externalDependencyClasspath.in(projectRef, configuration), state) match {
      case Some((_, Value(attrs))) =>
        for {
          attr <- attrs
          module <- attr.get(Keys.moduleID.key)
          artifact <- attr.get(Keys.artifact.key)
        } yield module.artifacts(artifact)
      case Some((_, Inc(incomplete))) =>
        val cause = Incomplete.allExceptions(incomplete).headOption
        cause.foreach(c => throw c)
        Seq.empty
      case _ => Seq.empty
    }
  }

  // We have to perform this configurations mapping because we're using externalDependencyClasspath
  // rather than libraryDependencies (to acquire transitive dependencies),  so we detect
  // module presence (in external classpath) instead of explicitly declared configurations.
  private def mapConfigurations(configurations: Seq[jb.Configuration]): Seq[jb.Configuration] = {
    val cs = configurations.map(c => if (c == jb.Configuration.IntegrationTest) jb.Configuration.Test else c).toSet

    if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test, jb.Configuration.Runtime)) {
      Seq(jb.Configuration.Compile)
    } else if (cs == Set(jb.Configuration.Compile, jb.Configuration.Test)) {
      Seq(jb.Configuration.Provided)
    } else {
      cs.toSeq
    }
  }
}
