package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.extractors.Extractor.Options
import org.jetbrains.sbt.structure._
import sbt._
import Utilities._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class ProjectExtractor(projectRef: ProjectRef) extends Extractor {
  override type Data = ProjectData

  private val ExportableConfigurations = Seq(Compile, Test, IntegrationTest)

  implicit val projectRefImplicit = projectRef

  override def extract(implicit state: State, options: Options): Option[Data] =
    for {
      name         <- projectSetting(Keys.name.in(Compile))
      organization <- projectSetting(Keys.organization.in(Compile))
      version      <- projectSetting(Keys.version.in(Compile))
      base         <- projectSetting(Keys.baseDirectory.in(Compile))
      target       <- projectSetting(Keys.target.in(Compile))
      dependencies <- new DependenciesExtractor(projectRef).extract
      build        <- new BuildExtractor(projectRef).extract
    } yield {
      val basePackages =
        projectSetting(SettingKeys.ideBasePackages.in(Keys.configuration)).getOrElse(Seq.empty) ++
        projectSetting(SettingKeys.sbtIdeaBasePackage.in(Keys.configuration)).map(_.toSeq).getOrElse(Seq.empty)

      val resolvers = projectTask(Keys.fullResolvers.in(Keys.configuration))
        .map(_.collect { case MavenRepository(name, root) => ResolverData(name, root) }).getOrElse(Seq.empty).toSet

      val configurations  = ExportableConfigurations.flatMap(c => extractConfiguration(c))
      val android         = new AndroidSdkPluginExtractor(projectRef).extract(state, options)
      val play2           = new Play2Extractor(projectRef).extract

      ProjectData(projectRef.project, name, organization, version, base,
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

      ConfigurationData(configuration.name, sources, resources, excludes, output)
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
}
