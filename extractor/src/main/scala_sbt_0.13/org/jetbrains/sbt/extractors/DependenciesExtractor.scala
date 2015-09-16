package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.extractors.Extractor.Options
import org.jetbrains.sbt.structure.{DependencyData, JarDependencyData, ModuleDependencyData, ProjectDependencyData}
import org.jetbrains.sbt.{structure => jb}
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
object DependenciesExtractor extends ModulesExtractor {
  override type Data = DependencyData

  override def extract(implicit state: State, options: Options): Option[DependencyData] = None

  def depExtractTask(implicit state: State, projectRef: ProjectRef) = Def.task {
    val projectDependencies = {
      val dep = Keys.buildDependencies.value
      dep.classpath.getOrElse(projectRef, Seq.empty).map { it =>
        val configurations = it.configuration.map(jb.Configuration.fromString).getOrElse(Seq.empty)
        ProjectDependencyData(it.project.project, configurations)
      }
    }
    DependencyData(projectDependencies, moduleDependencies.value, jarDependencies.value)
  }

  def moduleDependencies(implicit state: State, projectRef: ProjectRef) = {
    val depConfigs = getDependencyConfigurations
    Def.task[Seq[ModuleDependencyData]] {
      val config2mod = modulesIn.all(ScopeFilter(inProjects(ThisProject), inConfigurations(depConfigs: _*))).value

      val moduleToConfigurations = config2mod.flatMap {
        case (config, mods) => mods.map(mi => (mi, config))
      }.groupBy(_._1)
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
  }

  private def jarDependencies(implicit state: State, projectRef: ProjectRef) = {
    val depConfigs = getDependencyConfigurations
    Def.task[Seq[JarDependencyData]] {
      val config2jar = jarsIn.all(ScopeFilter(inProjects(ThisProject), inConfigurations(depConfigs: _*))).value
      val jarToConfigurations = config2jar
        .flatMap { case (config, jars) => jars.map(file => (file, config)) }
        .groupBy(_._1)
        .mapValues(_.unzip._2)
        .toSeq

      jarToConfigurations.map { case (file, configurations) =>
        JarDependencyData(file, mapConfigurations(configurations.map(c => jb.Configuration(c.name))))
      }
    }
  }

  private lazy val jarsIn = Def.task[(Configuration, Seq[File])]  {
  (Keys.configuration.value,
    (Keys.unmanagedClasspath ?? Seq.empty).value.map(_.data).filter(_.isFile))
  }

  private lazy val modulesIn = Def.task[(Configuration, Seq[ModuleID])] {
    val attrs = (Keys.externalDependencyClasspath ?? Seq.empty).value
    val mods = for {
      attr <- attrs
      module <- attr.get(Keys.moduleID.key)
      artifact <- attr.get(Keys.artifact.key)
    } yield module.artifacts(artifact)
    (Keys.configuration.value, mods)
}

  // We have to perform this configurations mapping because we're using externalDependencyClasspath
  // rather than libraryDependencies (to acquire transitive dependencies),  so we detect
  // module presence (in external classpath) instead of explicitly declared configurations.
  private def mapConfigurations(configurations: Seq[jb.Configuration])(implicit state: State, projectRef: ProjectRef): Seq[jb.Configuration] = {
    val jbTestConfigurations = getTestConfigurations.map(c => jb.Configuration(c.name))
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
