package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import sbt.Def.Initialize
import sbt.{Configuration => _, _}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
// don't remove this import: sbt.jetbrains.apiAdapter._ -- it shadows some symbols for sbt 1.0 compatibility
import sbt.jetbrains.apiAdapter._

/**
  * @author Nikolay Obedin
  * @since 4/10/15.
  */
class ProjectExtractor(
  projectRef: ProjectRef,
  name: String,
  organization: String,
  version: String,
  base: File,
  target: File,
  packagePrefix: Option[String],
  basePackages: Seq[String],
  allResolvers: Seq[Resolver],
  classDirectory: sbt.Configuration => Option[File],
  managedSourceDirectories: sbt.Configuration => Seq[File],
  unmanagedSourceDirectories: sbt.Configuration => Seq[File],
  managedResourceDirectories: sbt.Configuration => Seq[File],
  unmanagedResourceDirectories: sbt.Configuration => Seq[File],
  excludedDirectories: Seq[File],
  ideOutputDirectory: sbt.Configuration => Option[File],
  scalaOrganization: String,
  scalaInstance: Option[ScalaInstance],
  scalacOptions: Seq[String],
  javaHome: Option[File],
  javacOptions: Seq[String],
  compileOrder: CompileOrder,
  sourceConfigurations: Seq[sbt.Configuration],
  testConfigurations: Seq[sbt.Configuration],
  dependencies: DependencyData,
  android: Option[AndroidData],
  play2: Option[Play2Data],
  settingData: Seq[SettingData],
  taskData: Seq[TaskData],
  commandData: Seq[CommandData]
) {

  private[extractors] def extract: Seq[ProjectData] = {

    val resolvers = allResolvers.collect {
      case repo: MavenRepository => ResolverData(repo.name, repo.root)
    }.toSet

    val configurations =
      mergeConfigurations(
        sourceConfigurations.flatMap(extractConfiguration(Compile.name)) ++
          testConfigurations.flatMap(extractConfiguration(Test.name))
      )
    val projectData = ProjectData(
      projectRef.id,
      projectRef.build,
      name,
      organization,
      version,
      base,
      packagePrefix,
      basePackages,
      target,
      configurations,
      extractJava,
      extractScala,
      compileOrder.toString,
      android,
      dependencies,
      resolvers,
      play2,
      settingData,
      taskData,
      commandData
    )

    android match {
      case None => Seq(projectData)
      case Some(a) =>
        val deps = a.aars.map(
          aar =>
            ProjectDependencyData(aar.name, None, Configuration.Compile :: Nil)
        )
        // add aar module dependencies
        val projectDependencies = projectData.dependencies.projects
        val updatedProject = projectData.copy(
          dependencies = dependencies
            .copy(projects = Dependencies(projectDependencies.forTestSources,
              projectDependencies.forProductionSources ++ deps)
            )
        )
        updatedProject +: a.aars.map(
          _.project.copy(
            java = projectData.java,
            scala = projectData.scala,
            resolvers = projectData.resolvers,
            dependencies = projectData.dependencies
          )
        )
    }
  }

  private def extractConfiguration(
    ideConfig: String
  )(configuration: sbt.Configuration): Option[ConfigurationData] =
    classDirectory(configuration).map { sbtOutput =>
      val sources = {
        val managed = managedSourceDirectories(configuration)
        val unmanaged = unmanagedSourceDirectories(configuration)
        managed.map(DirectoryData(_, managed = true)) ++
          unmanaged.map(DirectoryData(_, managed = false))
      }

      val resources = {
        val managed = managedResourceDirectories(configuration)
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
      ConfigurationData(
        ideConfig,
        sources,
        resources,
        excludedDirectories,
        output
      )
    }

  /**
    * [[sbt.internal.inc.ScalaInstance]] has different structure in different sbt versions (0.13, 1.0, 1.3, 1.5)<br>
    * We need to convert it to our internal representation [[org.jetbrains.sbt.structure.ScalaData]]
    * which reflects structure of the latest sbt version (1.5.x)<br>
    * To do this there are two options:
    *   1. cross-publish sbt-structure-extractor plugin to more then 1 version: 0.13, 1.0, 1.3, 1.5
    *      and move some methods to [[sbt.jetbrains.apiAdapter]]
    *   1. use reflection
    *
    * We use reflection approach to
    *  1. easy project configuration
    *  1. decrease Scala Plugin size (we need to bundle all versions of sbt plugin)
    *
    * Structure of ScalaInstance in different sbt versions: {{{
    *    //in sbt 0.13.x
    *    class ScalaInstance(
    *        ...
    *        val libraryJar: File,
    *        val compilerJar: File,
    *        val extraJars: Seq[File],
    *        ...
    *    )
    *
    *    //in zinc-classpath 1.0.0
    *    class ScalaInstance(
    *        ...
    *        val libraryJar: File,
    *        val compilerJar: File,
    *        val allJars: Array[File],
    *        ...
    *    )
    *
    *    //in zinc-classpath 1.3.0
    *    class ScalaInstance(
    *        ...
    *        val libraryJars: Array[File],
    *        val compilerJar: File,
    *        val allJars: Array[File],
    *        ...
    *    )
    *
    *    //in zinc-classpath 1.5.0
    *    class ScalaInstance(
    *        ...
    *        val libraryJars: Array[File],
    *        val compilerJars: Array[File],
    *        val allJars: Array[File],
    *        ...
    *    )
    * }}}
    *
    * @note before `libraryJars: Array[File]` was introduced `libraryJar` contained single `scala-library.jar`.<br>
    *       Since Scala 3.0 it can contain extra `scala3-library_3.jar`.
    * @note before `compilerJars: Array[File]` was introduced `allJars` contained all compiler jars
    * @see SCL-19086
    */
  private def extractScala: Option[ScalaData] = scalaInstance.map { instance =>
    def normalize(files: Seq[File]): Seq[File] =
      files
        .filter(_.exists)
        // Sort files by absolute path (String) for better tests reproducibility
        //
        // NOTE 1: we do not use `files.sorted` because files ordering is OS-dependent
        // on Windows it's case-insensitive and on Unix it's case-sensitive
        // In Scala 3 some jar names are upper-case, so we need to sort by Strings (it's the same on all OS)
        //
        // NOTE 2: we cache absolute path in a tuple , in order `fs.resolve` is not called multiple times during the sorting
        .map(f => (f, f.getAbsolutePath))
        .sortBy(_._2: String)
        .map(_._1)

    val libraryJars  = normalize(extractLibraryJars(instance))
    val compilerJars = normalize((extractCompilerJars(instance).toSet -- libraryJars).toSeq)
    val extraJars    = normalize((instance.allJars.toSet -- libraryJars -- compilerJars).toSeq)

    ScalaData(
      scalaOrganization,
      instance.version,
      libraryJars,
      compilerJars,
      extraJars,
      scalacOptions
    )
  }

  /** @see docs of [[extractScala]] */
  private def extractLibraryJars(instance: ScalaInstance): Seq[File] =
    invokeMethodIfExists[Array[File]](instance, "libraryJars").map(_.toSeq).getOrElse(Seq(instance.libraryJar))

  /** @see docs of [[extractScala]] */
  private def extractCompilerJars(instance: ScalaInstance): Seq[File] = {
    // yes we need to fallback to allJars cause `compilerJar` contained only `scala-compiler.jar`
    invokeMethodIfExists[Array[File]](instance, "compilerJars").map(_.toSeq).getOrElse(instance.allJars.toSeq)
  }

  private def invokeMethodIfExists[R : ClassTag](obj: AnyRef, methodName: String): Option[R] =
    Try(obj.getClass.getMethod(methodName)) match {
      case Success(method)                   => Some(method.invoke(obj).asInstanceOf[R])
      case Failure(_: NoSuchMethodException) => None
      case Failure(ex)                       => throw ex
    }

  private def extractJava: Option[JavaData] =
    if (javaHome.isDefined || javacOptions.nonEmpty)
      Some(JavaData(javaHome, javacOptions))
    else None

  private def mergeConfigurations(
    configurations: Seq[ConfigurationData]
  ): Seq[ConfigurationData] =
    configurations
      .groupBy(_.id)
      .map {
        case (id, confs) =>
          val sources = confs.flatMap(_.sources).distinct
          val resources = confs.flatMap(_.resources).distinct
          val excludes = confs.flatMap(_.excludes).distinct
          ConfigurationData(id, sources, resources, excludes, confs.head.classes)
      }
      .toSeq
}

object ProjectExtractor extends SbtStateOps with TaskOps {

  private def settingInConfiguration[T](
    key: SettingKey[Seq[T]]
  )(implicit projectRef: ProjectRef, state: State) =
    (conf: sbt.Configuration) =>
      key.in(projectRef, conf).getOrElse(state, Seq.empty)

  private def taskInCompile[T](key: TaskKey[T])(implicit projectRef: ProjectRef,
                                                state: State) =
    key.in(projectRef, Compile).get(state)

  def taskDef: Initialize[Task[Seq[ProjectData]]] = Def.taskDyn {

    implicit val state: State = Keys.state.value
    implicit val projectRef: ProjectRef = sbt.Keys.thisProjectRef.value

    val idePackagePrefix =
      SettingKeys.idePackagePrefix.in(projectRef).find(state).flatten

    val basePackages =
      SettingKeys.ideBasePackages
        .in(projectRef)
        .find(state)
        .orElse(
          SettingKeys.sbtIdeaBasePackage.in(projectRef).find(state).map(_.toSeq)
        )
        .getOrElse(Seq.empty)

    def classDirectory(conf: sbt.Configuration) =
      Keys.classDirectory.in(projectRef, conf).find(state)

    val excludedDirectories =
      SettingKeys.ideExcludedDirectories
        .in(projectRef)
        .find(state)
        .orElse(
          SettingKeys.sbtIdeaExcludeFolders
            .in(projectRef)
            .find(state)
            .map(_.map(file))
        )
        .getOrElse(Seq.empty)

    def ideOutputDirectory(conf: sbt.Configuration) =
      SettingKeys.ideOutputDirectory.in(projectRef, conf).find(state).flatten

    val options = StructureKeys.sbtStructureOpts.value

    val managedSourceDirsInConfig =
      settingInConfiguration(Keys.managedSourceDirectories)
    val unmanagedSourceDirsInConfig =
      settingInConfiguration(Keys.unmanagedSourceDirectories)
    val managedResourceDirsInConfig =
      settingInConfiguration(Keys.managedResourceDirectories)
    val unmanagedResourceDirsInConfig =
      settingInConfiguration(Keys.unmanagedResourceDirectories)

    Def.task {

      val scalaOrganization =
        Keys.scalaOrganization.in(projectRef, Compile).value
      val scalaInstance =
        taskInCompile(Keys.scalaInstance).onlyIf(options.download).value
      val scalacOptions =
        taskInCompile(Keys.scalacOptions).onlyIf(options.download).value
      val javacOptions =
        taskInCompile(Keys.javacOptions).onlyIf(options.download).value

      val name = Keys.name.in(projectRef, Compile).value
      val organization = Keys.organization.in(projectRef, Compile).value
      val version = Keys.version.in(projectRef, Compile).value
      val base = Keys.baseDirectory.in(projectRef, Compile).value
      val target = Keys.target.in(projectRef, Compile).value
      val javaHome = Keys.javaHome.in(projectRef, Compile).value
      val compileOrder = Keys.compileOrder.in(projectRef, Compile).value

      new ProjectExtractor(
        projectRef,
        name,
        organization,
        version,
        base,
        target,
        idePackagePrefix,
        basePackages,
        Keys.fullResolvers.value,
        classDirectory,
        managedSourceDirsInConfig,
        unmanagedSourceDirsInConfig,
        managedResourceDirsInConfig,
        unmanagedResourceDirsInConfig,
        excludedDirectories,
        ideOutputDirectory,
        scalaOrganization,
        scalaInstance,
        scalacOptions.getOrElse(Seq.empty),
        javaHome,
        javacOptions.getOrElse(Seq.empty),
        compileOrder,
        StructureKeys.sourceConfigurations.value,
        StructureKeys.testConfigurations.value,
        StructureKeys.extractDependencies.value,
        StructureKeys.extractAndroid.value,
        StructureKeys.extractPlay2.value,
        StructureKeys.settingData.value,
        StructureKeys.taskData.value,
        StructureKeys.commandData.value.distinct
      ).extract
    }
  }

}
