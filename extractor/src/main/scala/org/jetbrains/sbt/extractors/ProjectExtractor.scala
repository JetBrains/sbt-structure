package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import sbt.Project.Initialize
import sbt._

/**
 * @author Nikolay Obedin
 * @since 4/10/15.
 */
class ProjectExtractor(projectRef: ProjectRef,
                       name: String,
                       organization: String,
                       version: String,
                       base: File,
                       target: File,
                       ideBasePackages: Seq[String],
                       sbtIdeaBasePackage: Option[String],
                       fullResolvers: Seq[Resolver],
                       classDirectory: sbt.Configuration => Option[File],
                       managedSourceDirectories: sbt.Configuration => Seq[File],
                       unmanagedSourceDirectories: sbt.Configuration => Seq[File],
                       managedResourceDirectories: sbt.Configuration => Seq[File],
                       unmanagedResourceDirectories: sbt.Configuration => Seq[File],
                       ideExcludedDirectories: Seq[File],
                       sbtIdeaExcludedFolders: Seq[String],
                       ideOutputDirectory: sbt.Configuration => Option[File],
                       scalaInstance: Option[ScalaInstance],
                       scalacOptions: Seq[String],
                       javaHome: Option[File],
                       javacOptions: Seq[String],
                       sourceConfigurations: Seq[sbt.Configuration],
                       testConfigurations: Seq[sbt.Configuration],
                       dependencies: DependencyData,
                       build: BuildData,
                       android: Option[AndroidData],
                       play2: Option[Play2Data]) {


  def extract: ProjectData = {
    val basePackages = ideBasePackages ++ sbtIdeaBasePackage.toSeq
    val resolvers = fullResolvers.collect {
      case MavenRepository(name, root) => ResolverData(name, root)
    }.toSet
    val configurations  = mergeConfigurations(sourceConfigurations.flatMap(extractConfiguration))
    ProjectData(projectRef.id, name, organization, version, base,
      basePackages, target, build, configurations,
      extractJava, extractScala, android, dependencies, resolvers, play2)
  }

  private def extractConfiguration(configuration: sbt.Configuration): Option[ConfigurationData] =
    classDirectory(configuration).map { sbtOutput =>
      val sources = {
        val managed   = managedSourceDirectories(configuration)
        val unmanaged = unmanagedSourceDirectories(configuration)
        managed.map(DirectoryData(_, managed = true)) ++ unmanaged.map(DirectoryData(_, managed = false))
      }

      val resources = {
        val managed   = managedResourceDirectories(configuration)
        val unmanaged = unmanagedResourceDirectories(configuration)
        managed.map(DirectoryData(_, managed = true)) ++ unmanaged.map(DirectoryData(_, managed = false))
      }

      val excludes = ideExcludedDirectories ++ sbtIdeaExcludedFolders.map(file)
      val output = ideOutputDirectory(configuration).getOrElse(sbtOutput)

      ConfigurationData(mapConfiguration(configuration).name, sources, resources, excludes, output)
    }

  private def mapConfiguration(configuration: sbt.Configuration): sbt.Configuration =
    if (testConfigurations.contains(configuration)) Test else configuration

  private def extractScala: Option[ScalaData] = scalaInstance.map { instance =>
    val extraJars = instance.extraJars.filter(_.getName.contains("reflect"))
    ScalaData(instance.version, instance.libraryJar, instance.compilerJar, extraJars, scalacOptions)
  }

  private def extractJava: Option[JavaData] =
    if (javaHome.isDefined || javacOptions.nonEmpty) Some(JavaData(javaHome, javacOptions)) else None

  private def mergeConfigurations(configurations: Seq[ConfigurationData]): Seq[ConfigurationData] =
    configurations.groupBy(_.id).map { case (id, confs) =>
      val sources = confs.flatMap(_.sources)
      val resources = confs.flatMap(_.resources)
      val excludes = confs.flatMap(_.excludes)
      ConfigurationData(id, sources, resources, excludes, confs.head.classes)
    }.toSeq
}

object ProjectExtractor extends SbtStateOps {
  def apply(projectRef: ProjectRef)(implicit state: State, options: Options): Option[ProjectData] = {
    implicit val implicitProjectRef = projectRef
    for {
      name         <- projectSetting(Keys.name.in(Compile))
      organization <- projectSetting(Keys.organization.in(Compile))
      version      <- projectSetting(Keys.version.in(Compile))
      base         <- projectSetting(Keys.baseDirectory.in(Compile))
      target       <- projectSetting(Keys.target.in(Compile))
    } yield {
      val ideBasePackages = projectSetting(SettingKeys.ideBasePackages.in(Keys.configuration)).getOrElse(Seq.empty)
      val sbtIdeaBasePackage = projectSetting(SettingKeys.sbtIdeaBasePackage.in(Keys.configuration)).flatten
      val fullResolvers = projectTask(Keys.fullResolvers.in(Keys.configuration)).getOrElse(Seq.empty)

      def classDirectory(conf: sbt.Configuration) = projectSetting(Keys.classDirectory.in(conf))
      def inConfiguration[T](key: SettingKey[Seq[T]])(conf: sbt.Configuration) = projectSetting(key.in(conf)).getOrElse(Seq.empty)

      val ideExcludedDirectories = projectSetting(SettingKeys.ideExcludedDirectories).getOrElse(Seq.empty)
      val sbtIdeaExcludeFolders = projectSetting(SettingKeys.sbtIdeaExcludeFolders).getOrElse(Seq.empty)
      def ideOutputDirectory(conf: sbt.Configuration) = projectSetting(SettingKeys.ideOutputDirectory.in(conf)).flatten

      val scalaInstance = options.download.option(projectTask(Keys.scalaInstance.in(Compile))).flatten
      val scalacOptions = options.download.option(projectTask(Keys.scalacOptions.in(Compile))).flatten.getOrElse(Seq.empty)
      val javaHome = projectSetting(Keys.javaHome.in(Compile)).flatten
      val javacOptions = options.download.option(projectTask(Keys.javacOptions.in(Compile))).flatten.getOrElse(Seq.empty)

      val dependencies = DependenciesExtractor.apply
      val build = projectTask(StructureKeys.extractBuild).get
      val play2 = projectTask(StructureKeys.extractPlay2).flatten
      val android = projectTask(StructureKeys.extractAndroid).flatten

      val sourceConfigurations = StructureKeys.sourceConfigurations.in(projectRef).get(state)
      val testConfigurations = StructureKeys.testConfigurations.in(projectRef).get(state)

      new ProjectExtractor(
        projectRef, name, organization, version, base, target,
        ideBasePackages, sbtIdeaBasePackage,
        fullResolvers, classDirectory,
        inConfiguration(Keys.managedSourceDirectories),
        inConfiguration(Keys.unmanagedSourceDirectories),
        inConfiguration(Keys.managedResourceDirectories),
        inConfiguration(Keys.unmanagedResourceDirectories),
        ideExcludedDirectories,
        sbtIdeaExcludeFolders,
        ideOutputDirectory,
        scalaInstance,
        scalacOptions,
        javaHome,
        javacOptions,
        sourceConfigurations,
        testConfigurations,
        dependencies,
        build,
        android,
        play2
      ).extract
    }
  }
}

