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
                       basePackages: Seq[String],
                       fullResolvers: Seq[Resolver],
                       classDirectory: sbt.Configuration => Option[File],
                       managedSourceDirectories: sbt.Configuration => Seq[File],
                       unmanagedSourceDirectories: sbt.Configuration => Seq[File],
                       managedResourceDirectories: sbt.Configuration => Seq[File],
                       unmanagedResourceDirectories: sbt.Configuration => Seq[File],
                       excludedDirectories: Seq[File],
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


  private[extractors] def extract: ProjectData = {
    val resolvers = fullResolvers.collect {
      case MavenRepository(name, root) => ResolverData(name, root)
    }.toSet
    val configurations  =
      mergeConfigurations(sourceConfigurations.flatMap(extractConfiguration))
    ProjectData(projectRef.id, name, organization, version, base,
      basePackages, target, build, configurations,
      extractJava, extractScala, android, dependencies, resolvers, play2)
  }

  private def extractConfiguration(configuration: sbt.Configuration): Option[ConfigurationData] =
    classDirectory(configuration).map { sbtOutput =>
      val sources = {
        val managed   = managedSourceDirectories(configuration)
        val unmanaged = unmanagedSourceDirectories(configuration)
        managed.map(DirectoryData(_, managed = true)) ++
          unmanaged.map(DirectoryData(_, managed = false))
      }

      val resources = {
        val managed   = managedResourceDirectories(configuration)
        val unmanaged = unmanagedResourceDirectories(configuration)
        managed.map(DirectoryData(_, managed = true)) ++
          unmanaged.map(DirectoryData(_, managed = false))
      }

      val output = ideOutputDirectory(configuration).getOrElse(sbtOutput)

      ConfigurationData(mapConfiguration(configuration).name,
        sources, resources, excludedDirectories, output)
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
      val sources   = confs.flatMap(_.sources)
      val resources = confs.flatMap(_.resources)
      val excludes  = confs.flatMap(_.excludes)
      ConfigurationData(id, sources, resources, excludes, confs.head.classes)
    }.toSeq
}

object ProjectExtractor extends SbtStateOps with TaskOps {

  def taskDef: Initialize[Task[ProjectData]] =
    ( sbt.Keys.state
    , sbt.Keys.thisProjectRef
    , StructureKeys.sbtStructureOpts
    , sbt.Keys.fullResolvers
    , StructureKeys.extractDependencies
    , StructureKeys.extractBuild
    , StructureKeys.extractAndroid
    , StructureKeys.extractPlay2
    , StructureKeys.sourceConfigurations
    , StructureKeys.testConfigurations
    ) flatMap {
      (state, projectRef, options, fullResolvers, dependencies,
        build, android, play2, sourceConfigurations, testConfigurations) =>

        val name         = Keys.name.in(projectRef, Compile).get(state)
        val organization = Keys.organization.in(projectRef, Compile).get(state)
        val version      = Keys.version.in(projectRef, Compile).get(state)
        val base         = Keys.baseDirectory.in(projectRef, Compile).get(state)
        val target       = Keys.target.in(projectRef, Compile).get(state)
        val javaHome     = Keys.javaHome.in(projectRef, Compile).find(state).flatten

        val basePackages =
          SettingKeys.ideBasePackages.in(projectRef).find(state)
            .orElse(SettingKeys.sbtIdeaBasePackage.in(projectRef).find(state).map(_.toSeq))
            .getOrElse(Seq.empty)

        def classDirectory(conf: sbt.Configuration) =
          Keys.classDirectory.in(projectRef, conf).find(state)

        def inConfiguration[T](key: SettingKey[Seq[T]])(conf: sbt.Configuration) =
          key.in(projectRef, conf).getOrElse(state, Seq.empty)

        val excludedDirectories =
          SettingKeys.ideExcludedDirectories.in(projectRef).find(state)
            .orElse(SettingKeys.sbtIdeaExcludeFolders.in(projectRef).find(state).map(_.map(file)))
            .getOrElse(Seq.empty)

        def ideOutputDirectory(conf: sbt.Configuration) =
          SettingKeys.ideOutputDirectory.in(projectRef, conf).find(state).flatten

        val scalaInstanceTask =
          Keys.scalaInstance.in(projectRef, Compile).get(state).onlyIf(options.download)
        val scalacOptionsTask =
          Keys.scalacOptions.in(projectRef, Compile).get(state).onlyIf(options.download)
        val javacOptionsTask =
          Keys.javacOptions.in(projectRef, Compile).get(state).onlyIf(options.download)

        for {
          scalaInstance <- scalaInstanceTask
          scalacOptions <- scalacOptionsTask
          javacOptions  <- javacOptionsTask
        } yield {
          new ProjectExtractor(
            projectRef, name, organization, version, base, target,
            basePackages,
            fullResolvers,
            classDirectory,
            inConfiguration(Keys.managedSourceDirectories),
            inConfiguration(Keys.unmanagedSourceDirectories),
            inConfiguration(Keys.managedResourceDirectories),
            inConfiguration(Keys.unmanagedResourceDirectories),
            excludedDirectories,
            ideOutputDirectory,
            scalaInstance, scalacOptions.getOrElse(Seq.empty),
            javaHome, javacOptions.getOrElse(Seq.empty),
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

