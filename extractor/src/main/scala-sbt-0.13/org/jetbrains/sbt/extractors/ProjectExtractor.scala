package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import sbt.Def.Initialize
import sbt.{Configuration => _, _}

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
                       allResolvers: Seq[Resolver],
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
                       play2: Option[Play2Data],
                       settingData: Seq[SettingData],
                       taskData: Seq[TaskData],
                       commandData: Seq[CommandData]
                      ) {


  private[extractors] def extract: Seq[ProjectData] = {

    val resolvers = allResolvers.collect {
      case MavenRepository(repoName, root) => ResolverData(repoName, root)
    }.toSet

    val configurations =
      mergeConfigurations(
        sourceConfigurations.flatMap(extractConfiguration(Compile.name) _) ++
          testConfigurations.flatMap(extractConfiguration(Test.name) _)
      )
    val projectData = ProjectData(
      projectRef.id, projectRef.build, name, organization, version, base,
      basePackages, target, build, configurations,
      extractJava, extractScala, android, dependencies, resolvers, play2,
      settingData, taskData, commandData)

    android match {
      case None => Seq(projectData)
      case Some(a) =>
        val deps = a.aars.map(aar => ProjectDependencyData(aar.name, Configuration.Compile :: Nil))
        // add aar module dependencies
        val updatedProject = projectData.copy(dependencies = dependencies.copy(projects = projectData.dependencies.projects ++ deps))
        updatedProject +: a.aars.map(_.project.copy(
          java         = projectData.java,
          scala        = projectData.scala,
          build        = projectData.build,
          resolvers    = projectData.resolvers,
          dependencies = projectData.dependencies
        ))
    }
  }

  private def extractConfiguration(ideConfig: String)(configuration: sbt.Configuration): Option[ConfigurationData] =
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

      /*
        * IntelliJ has a more limited model of configuration than sbt/ivy, so we need to map them to one of the types
        * we can handle: Test or Compile. This is the `ideConfig`
        * This mapping is not perfect because we depend on the configuration extension mechanism to detect what is a test
        * config, and IntelliJ can not model config dependencies fully. The aim is to reduce amount of "red code" that
        * hampers productivity.
        */
      ConfigurationData(ideConfig, sources, resources, excludedDirectories, output)
    }

  private def extractScala: Option[ScalaData] = scalaInstance.map { instance =>
    ScalaData(instance.version, instance.jars.filter(_.exists).sorted, scalacOptions)
  }

  private def extractJava: Option[JavaData] =
    if (javaHome.isDefined || javacOptions.nonEmpty) Some(JavaData(javaHome, javacOptions)) else None

  private def mergeConfigurations(configurations: Seq[ConfigurationData]): Seq[ConfigurationData] =
    configurations.groupBy(_.id).map { case (id, confs) =>
      val sources   = confs.flatMap(_.sources).distinct
      val resources = confs.flatMap(_.resources).distinct
      val excludes  = confs.flatMap(_.excludes).distinct
      ConfigurationData(id, sources, resources, excludes, confs.head.classes)
    }.toSeq
}

object ProjectExtractor extends SbtStateOps with TaskOps {

  private def settingInConfiguration[T](key: SettingKey[Seq[T]])(implicit projectRef: ProjectRef, state: State) =
    (conf: sbt.Configuration) => key.in(projectRef, conf).getOrElse(state, Seq.empty)

  private def taskInCompile[T](key: TaskKey[T])(implicit projectRef: ProjectRef, state: State) =
    key.in(projectRef, Compile).get(state)

  def taskDef: Initialize[Task[Seq[ProjectData]]] = Def.taskDyn {

    implicit val state = Keys.state.value
    implicit val projectRef = sbt.Keys.thisProjectRef.value

    val basePackages =
      SettingKeys.ideBasePackages.in(projectRef).find(state)
        .orElse(SettingKeys.sbtIdeaBasePackage.in(projectRef).find(state).map(_.toSeq))
        .getOrElse(Seq.empty)

    def classDirectory(conf: sbt.Configuration) =
      Keys.classDirectory.in(projectRef, conf).find(state)

    val excludedDirectories =
      SettingKeys.ideExcludedDirectories.in(projectRef).find(state)
        .orElse(SettingKeys.sbtIdeaExcludeFolders.in(projectRef).find(state).map(_.map(file)))
        .getOrElse(Seq.empty)

    def ideOutputDirectory(conf: sbt.Configuration) =
      SettingKeys.ideOutputDirectory.in(projectRef, conf).find(state).flatten

    val options = StructureKeys.sbtStructureOpts.value

    val managedSourceDirsInConfig = settingInConfiguration(Keys.managedSourceDirectories)
    val unmanagedSourceDirsInConfig = settingInConfiguration(Keys.unmanagedSourceDirectories)
    val managedResourceDirsInConfig = settingInConfiguration(Keys.managedResourceDirectories)
    val unmanagedResourceDirsInConfig = settingInConfiguration(Keys.unmanagedResourceDirectories)

    Def.task {

      val scalaInstance = taskInCompile(Keys.scalaInstance).onlyIf(options.download).value
      val scalacOptions = taskInCompile(Keys.scalacOptions).onlyIf(options.download).value
      val javacOptions = taskInCompile(Keys.javacOptions).onlyIf(options.download).value

      val name = Keys.name.in(projectRef, Compile).value
      val organization = Keys.organization.in(projectRef, Compile).value
      val version = Keys.version.in(projectRef, Compile).value
      val base = Keys.baseDirectory.in(projectRef, Compile).value
      val target = Keys.target.in(projectRef, Compile).value
      val javaHome = Keys.javaHome.in(projectRef, Compile).value

      new ProjectExtractor(
        projectRef, name, organization, version, base, target,
        basePackages,
        Keys.fullResolvers.value,
        classDirectory,
        managedSourceDirsInConfig,
        unmanagedSourceDirsInConfig,
        managedResourceDirsInConfig,
        unmanagedResourceDirsInConfig,
        excludedDirectories,
        ideOutputDirectory,
        scalaInstance, scalacOptions.getOrElse(Seq.empty),
        javaHome, javacOptions.getOrElse(Seq.empty),
        UtilityTasks.sourceConfigurations.value,
        UtilityTasks.testConfigurations.value,
        DependenciesExtractor.taskDef.value,
        BuildExtractor.taskDef.value,
        tasks.extractAndroidSdkPlugin.value,
        Play2Extractor.taskDef.value,
        KeysExtractor.settingData.value,
        KeysExtractor.taskData.value,
        KeysExtractor.commandData.value
      ).extract
    }
  }

}