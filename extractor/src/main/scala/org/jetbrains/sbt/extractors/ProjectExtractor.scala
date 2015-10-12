package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import Utilities._
import sbt._
import Utilities._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class ProjectExtractor(implicit projectRef: ProjectRef) extends Extractor with Configurations {
  def extract(implicit state: State, options: Options): Option[ProjectData] =
    for {
      name         <- projectSetting(Keys.name.in(Compile))
      organization <- projectSetting(Keys.organization.in(Compile))
      version      <- projectSetting(Keys.version.in(Compile))
      base         <- projectSetting(Keys.baseDirectory.in(Compile))
      target       <- projectSetting(Keys.target.in(Compile))
      dependencies <- DependenciesExtractor.apply
      build        <- BuildExtractor.apply
    } yield {
      val basePackages =
        projectSetting(SettingKeys.ideBasePackages.in(Keys.configuration)).getOrElse(Seq.empty) ++
        projectSetting(SettingKeys.sbtIdeaBasePackage.in(Keys.configuration)).map(_.toSeq).getOrElse(Seq.empty)

      val resolvers = projectTask(Keys.fullResolvers.in(Keys.configuration))
        .map(_.collect { case MavenRepository(name, root) => ResolverData(name, root) }).getOrElse(Seq.empty).toSet

      val configurations  = mergeConfigurations(getSourceConfigurations.flatMap(c => extractConfiguration(c)))
      val android         = AndroidSdkPluginExtractor.apply
      val play2           = Play2Extractor.apply

      ProjectData(projectRef.id, name, organization, version, base,
        basePackages, target, build, configurations,
        extractJava, extractScala, android, dependencies, resolvers, play2)
    }

  private def extractConfiguration(configuration: sbt.Configuration)(implicit state: State): Option[ConfigurationData] =
    projectSetting(Keys.classDirectory.in(configuration)).map { output =>
      val sources = {
        val managed   = projectSetting(Keys.managedSourceDirectories.in(configuration)).getOrElse(Seq.empty)
        val unmanaged = projectSetting(Keys.unmanagedSourceDirectories.in(configuration)).getOrElse(Seq.empty)
        managed.map(DirectoryData(_, managed = true)) ++ unmanaged.map(DirectoryData(_, managed = false))
      }

      val resources = {
        val managed   = projectSetting(Keys.managedResourceDirectories.in(configuration)).getOrElse(Seq.empty)
        val unmanaged = projectSetting(Keys.unmanagedResourceDirectories.in(configuration)).getOrElse(Seq.empty)
        managed.map(DirectoryData(_, managed = true)) ++ unmanaged.map(DirectoryData(_, managed = false))
      }

      val excludes =
        projectSetting(SettingKeys.ideExcludedDirectories.in(configuration)).getOrElse(Seq.empty) ++
        projectSetting(SettingKeys.sbtIdeaExcludeFolders.in(configuration)).map(_.map(file)).getOrElse(Seq.empty)

      ConfigurationData(mapConfiguration(configuration).name, sources, resources, excludes, output)
    }

  private def extractScala(implicit state: State, options: Options): Option[ScalaData] =
    options.download.option {
      projectTask(Keys.scalaInstance.in(Compile)).map { instance =>
        val options = projectTask(Keys.scalacOptions.in(Compile)).getOrElse(Seq.empty)
        val extraJars = instance.extraJars.filter(_.getName.contains("reflect"))
        ScalaData(instance.version, instance.libraryJar, instance.compilerJar, extraJars, options)
      }
    }.flatten

  private def extractJava(implicit state: State, options: Options): Option[JavaData] =
    options.download.option {
      val home = projectSetting(Keys.javaHome.in(Compile)).collect { case Some(opts) => opts }
      val options = projectTask(Keys.javacOptions.in(Compile)).getOrElse(Seq.empty)
      if (home.isDefined || options.nonEmpty) Some(JavaData(home, options)) else None
    }.flatten

  private def mergeConfigurations(configurations: Seq[ConfigurationData]): Seq[ConfigurationData] =
    configurations.groupBy(_.id).map { case (id, confs) =>
      val sources = confs.flatMap(_.sources)
      val resources = confs.flatMap(_.resources)
      val excludes = confs.flatMap(_.excludes)
      ConfigurationData(id, sources, resources, excludes, confs.head.classes)
    }.toSeq
}

object ProjectExtractor {
  def apply(projectRef: ProjectRef)(implicit state: State, options: Options): Option[ProjectData] =
    new ProjectExtractor()(projectRef).extract
}
