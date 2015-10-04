package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.extractors.Extractor.Options
import org.jetbrains.sbt.structure._
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
object ProjectExtractor extends ExtractorWithConfigurations {
  type Data = ProjectData

  val projectData = taskKey[Option[ProjectData]]("Get project data")

  def projectExtractTask(options: Options)(implicit state: State, projectRef: ProjectRef) = {
    projectData := {
      val name = Keys.name.in(Compile).value
      val organization = Keys.organization.in(Compile).value
      val version = Keys.version.in(Compile).value
      val base = Keys.baseDirectory.in(Compile).value
      val target = Keys.target.in(Compile).value
      Keys.streams.value.log.info(s"Extracting structure from ${projectRef.project}")
      for {
        build <- new BuildExtractor(projectRef).extract(state, options)
      } yield {
        val basePackages =
          (SettingKeys.ideBasePackages.in(Keys.configuration) ?? Seq.empty).value ++
            (SettingKeys.sbtIdeaBasePackage.in(Keys.configuration) ?? None).value
        val resolvers = (Keys.fullResolvers.in(Keys.configuration).?).value
          .map(_.collect { case MavenRepository(name, root) => ResolverData(name, root) }).getOrElse(Seq.empty).toSet

        val configurations  = mergeConfigurations(getSourceConfigurations.flatMap(c => extractConfiguration(c)))
        val android = new AndroidSdkPluginExtractor(projectRef).extract(state, options)
        val play2 = new Play2Extractor(projectRef).extract(state, options)

        val dependencies = DependenciesExtractor.depExtractTask.value
        ProjectData(projectRef.project, name, organization, version, base,
          basePackages, target, build, configurations,
          extractJava.value, extractScala.value, android, dependencies, resolvers, play2)
      }
    }
  }

  private def extractConfiguration(configuration: sbt.Configuration)(implicit state: State, projectRef: ProjectRef): Option[ConfigurationData] =
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

  lazy val extractScala = Def.task[Option[ScalaData]] {
      val instance = Keys.scalaInstance.in(Compile).value
      val options = Keys.scalacOptions.in(Compile).value
      val extraJars = instance.extraJars.filter(_.getName.contains("reflect"))
      Some(ScalaData(instance.version, instance.libraryJar, instance.compilerJar, extraJars, options))
  }

  lazy val extractJava = Def.task[Option[JavaData]] {
      val home = Keys.javaHome.in(Compile).value
      val options = Keys.javacOptions.in(Compile).value
      if (home.isDefined || options.nonEmpty) Some(JavaData(home, options)) else None
  }

  private def mergeConfigurations(configurations: Seq[ConfigurationData]): Seq[ConfigurationData] =
    configurations.groupBy(_.id).map { case (id, confs) =>
      val sources = confs.flatMap(_.sources)
      val resources = confs.flatMap(_.resources)
      val excludes = confs.flatMap(_.excludes)
      ConfigurationData(id, sources, resources, excludes, confs.head.classes)
    }.toSeq

  override def extract(implicit state: State, options: Options): Option[ProjectData] = None
}
