package org.jetbrains.sbt
package structure

import java.io.File

import java.net.URI


/**
 * @author Pavel Fatin
 * @author Nikolay Obedin
 */

case class Configuration(name: String) {
  override def toString: String = name
}

object Configuration {
  val Compile: Configuration  = Configuration("compile")
  val Test: Configuration     = Configuration("test")
  val Runtime: Configuration  = Configuration("runtime")
  val Provided: Configuration = Configuration("provided")

  def fromString(confStr: String): Seq[Configuration] =
    if (confStr.isEmpty) Seq.empty else confStr.split(";").map(c => Configuration(c))
}

/**
 * Represent specified build. Corresponds to IDEA project.
 *
 * @param projects List of projects in build
 * @param repository List of libraries in build
 * @param localCachePath Path to a place where Ivy downloads artifacts. Usually ~/.ivy2/cache
 */
case class StructureData(sbtVersion: String,
                         builds: Seq[BuildData],
                         projects: Seq[ProjectData],
                         repository: Option[RepositoryData],
                         localCachePath: Option[File])

/**
 * Represents single project in build. Corresponds to IDEA module.
 *
 * @param basePackages List of packages to use as base prefixes in chaining
 * @param target Compiler output directory (value of `target` key)
 * @param mainSourceDirectories List of source directories in all available source configurations.
 *                              Unlike [[org.jetbrains.sbt.structure.ConfigurationData#sources()]], which was obtained from <code>managedSourceDirectories</code>
 *                              and <code>unmanagedSourceDirectories</code> keys, this value is sourced from the <code>sourceDirectory</code> key.
 *                              In a simple sbt project in e.g. compile configuration [[org.jetbrains.sbt.structure.ConfigurationData#sources()]] will be
 *                              (the project path at the beginning of these paths is skipped) <ul>
 *                              <li><code>/target/scala-3.3.3/src_managed/main</li>
 *                              <li><code>/src/main/scala</code></li>
 *                              <li><code>/src/main/scala-3</code></li>
 *                              <li><code>/src/main/java</code></li>
 *                              </ul>
 *                              But value of this field will be just <code>/src/main</code>.
 *                              It is needed to identify ContentRootData#rootPath in the Scala plugin.
 *
 */
case class ProjectData(
  id: String,
  buildURI: URI,
  name: String,
  organization: String,
  version: String,
  base: File,
  packagePrefix: Option[String],
  basePackages: Seq[String],
  target: File,
  configurations: Seq[ConfigurationData],
  java: Option[JavaData],
  scala: Option[ScalaData],
  compileOrder: String,
  dependencies: DependencyData,
  resolvers: Set[ResolverData],
  play2: Option[Play2Data],
  twirl: Option[TwirlData],
  settings: Seq[SettingData],
  tasks: Seq[TaskData],
  commands: Seq[CommandData],
  mainSourceDirectories: Seq[File],
  testSourceDirectories: Seq[File]
)

case class SettingData(label: String, description: Option[String], rank: Int, stringValue: Option[String])
case class TaskData(label: String, description: Option[String], rank: Int)
case class CommandData(name: String, help: Seq[(String,String)])

/**
 * Information about build dependencies and implicit imports for proper editing of .sbt files
 */
sealed abstract class BuildData extends Product {
  val uri: URI
  val imports: Seq[String]
  val classes: Seq[File]
  val docs: Seq[File]
  val sources: Seq[File]
}
// hack a case class with private constructor to ensure some invariants in constructions
object BuildData {
  private case class BuildDataImpl (uri: URI, imports: Seq[String], classes: Seq[File], docs: Seq[File], sources: Seq[File]) extends BuildData
  private def sort(files: Seq[File]): Seq[File] = files.sortBy(_.getCanonicalPath)

  def apply(uri: URI, imports: Seq[String], classes: Seq[File], docs: Seq[File], sources: Seq[File]): BuildData =
    BuildDataImpl(
      uri.normalize(),
      imports.sorted,
      sort(classes),
      sort(docs),
      sort(sources)
    )
}

/**
 * Lists of directories in specified configuration
 *
 * @param id Name of configuration, usually "compile" or "test"
 * @param sources List of source directories
 * @param resources List of resource directories
 * @param excludes List of excluded directories
 * @param classes Directory containing compiled classes and copied resources
 */
case class ConfigurationData(id: String,
                             sources: Seq[DirectoryData],
                             resources: Seq[DirectoryData],
                             excludes: Seq[File],
                             classes: File)

case class DirectoryData(file: File, managed: Boolean)

case class CompilerOptions(configuration: Configuration, options: Seq[String])

case class JavaData(home: Option[File], options: Seq[CompilerOptions])

/**
 * Analog of `sbt.internal.inc.ScalaInstance`
 *
 * @param libraryJars             contains scala-library.jar and (in case of Scala 3) scala3-library_3.jar
 * @param compilerJars            contains all jars required to instantiate scala  compiler<br>
 *                                (except for library jars, which should also be included when creating a compiler instance)
 * @param extraJars               other jars, usually contain jars required to run ScalaDoc
 * @param compilerBridgeBinaryJar scala3-compiler-bridge_3.x.jar, extracted from `sbt.Keys.scalaCompilerBridgeBinaryJar`
 */
case class ScalaData(
  organization: String,
  version: String,
  libraryJars: Seq[File],
  compilerJars: Seq[File],
  extraJars: Seq[File],
  compilerBridgeBinaryJar: Option[File],
  options: Seq[CompilerOptions]
) {
  def allJars: Seq[File] = libraryJars ++ compilerJars ++ extraJars
  def allCompilerJars: Seq[File] = libraryJars ++ compilerJars
}

case class DependencyData(projects: Dependencies[ProjectDependencyData],
                          modules: Dependencies[ModuleDependencyData],
                          jars: Dependencies[JarDependencyData])

/**
 * @param forProduction dependencies that should go to the main module.
 *                      If separate modules for production/test sources are disabled, then all dependencies are put in this field.
 * @param forTest dependencies that should go to the test module.
 *                If separate modules for production/test sources are disabled, then this field contains en empty Seq.
 */
case class Dependencies[T](forProduction: Seq[T], forTest: Seq[T])

/**
 * Inter-project dependency
 *
 * @param project What project to depend on
 */
case class ProjectDependencyData(project: String, buildURI: Option[URI], configurations: Seq[Configuration])

/**
 * External library dependency
 *
 * @param id Library identifier
 */
case class ModuleDependencyData(id: ModuleIdentifier, configurations: Seq[Configuration])

/**
 * Unmanaged dependency
 *
 * @param file File to depend on
 */
case class JarDependencyData(file: File, configurations: Seq[Configuration])

/**
 * Library identifier
 *
 * @param revision AKA version
 */
case class ModuleIdentifier(organization: String,
                            name: String,
                            revision: String,
                            artifactType: String,
                            classifier: String) {
  def key: Iterable[String] = productIterator.toIterable.asInstanceOf[Iterable[String]]
}

/**
 * External library data. Corresponds to a project-level library in IDEA.
 *
 * @param id Library identifier
 * @param binaries List of binary jars
 * @param docs List of javadoc jars
 * @param sources List of source jars
 */
case class ModuleData(id: ModuleIdentifier,
                      binaries: Set[File],
                      docs: Set[File],
                      sources: Set[File])

/**
 * List of external libraries
 */
case class RepositoryData(modules: Seq[ModuleData])

/**
 * Repository used to resolve external library dependencies
 *
 * @param root URL or local path to a repo
 */
case class ResolverData(name: String, root: String)

/**
 * List of parameters specific to Play projects
 */
case class Play2Data(playVersion: Option[String],
                     routesImports: Seq[String],
                     confDirectory: Option[File],
                     sourceDirectory: File)

/**
 * List of parameters specific to projects which have twirl templates set-up (even without the Play framework)
 */
case class TwirlData(
  templatesImports: Seq[String]
)