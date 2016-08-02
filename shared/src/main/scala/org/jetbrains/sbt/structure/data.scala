package org.jetbrains.sbt
package structure

import java.io.File


/**
 * @author Pavel Fatin
 * @author Nikolay Obedin
 */

case class Configuration(name: String) {
  override def toString = name
}

object Configuration {
  val Compile = Configuration("compile")
  val Test = Configuration("test")
  val Runtime = Configuration("runtime")
  val Provided = Configuration("provided")

  def fromString(confStr: String): Seq[Configuration] =
    if (confStr.isEmpty) Seq.empty else confStr.split(";").map(c => Configuration(c))
}

/**
 * Represent specified build. Corresponds to IDEA project.
 * @param projects List of projects in build
 * @param repository List of libraries in build
 * @param localCachePath Path to a place where Ivy downloads artifacts. Usually ~/.ivy2/cache
 */
case class StructureData(sbtVersion: String,
                         projects: Seq[ProjectData],
                         repository: Option[RepositoryData],
                         localCachePath: Option[String])

/**
 * Represents single project in build. Corresponds to IDEA module.
 * @param basePackages List of packages to use as base prefixes in chaining
 * @param target Compiler output directory (value of `target` key)
 */
case class ProjectData(id: String,
                       name: String,
                       organization: String,
                       version: String,
                       base: File,
                       basePackages: Seq[String],
                       target: File,
                       build: BuildData,
                       configurations: Seq[ConfigurationData],
                       java: Option[JavaData],
                       scala: Option[ScalaData],
                       android: Option[AndroidData],
                       dependencies: DependencyData,
                       resolvers: Set[ResolverData],
                       play2: Option[Play2Data])

/**
 * Information about build dependencies and implicit imports for proper editing of .sbt files
 */
sealed abstract class BuildData extends Product {
  val imports: Seq[String]
  val classes: Seq[File]
  val docs: Seq[File]
  val sources: Seq[File]
}
// hack a case class with private constructor to ensure some invariants in constructions
object BuildData {
  private case class BuildDataImpl (imports: Seq[String], classes: Seq[File], docs: Seq[File], sources: Seq[File]) extends BuildData
  private def sort(files: Seq[File]): Seq[File] = files.sortBy(_.getCanonicalPath)

  def apply(imports: Seq[String], classes: Seq[File], docs: Seq[File], sources: Seq[File]): BuildData =
    BuildDataImpl(
      imports.sorted,
      sort(classes),
      sort(docs),
      sort(sources)
    )
}

/**
 * Lists of directories in specified configuration
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

case class JavaData(home: Option[File], options: Seq[String])

case class ScalaData(version: String,
                     libraryJar: File,
                     compilerJar: File,
                     extraJars: Seq[File],
                     options: Seq[String])

case class DependencyData(projects: Seq[ProjectDependencyData],
                          modules: Seq[ModuleDependencyData],
                          jars: Seq[JarDependencyData])

/**
 * Inter-project dependency
 * @param project What project to depend on
 */
case class ProjectDependencyData(project: String, configuration: Seq[Configuration])

/**
 * External library dependency
 * @param id Library identifier
 */
case class ModuleDependencyData(id: ModuleIdentifier, configurations: Seq[Configuration])

/**
 * Unmanaged dependency
 * @param file File to depend on
 */
case class JarDependencyData(file: File, configurations: Seq[Configuration])

/**
 * Library identifier
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
 * @param root URL or local path to a repo
 */
case class ResolverData(name: String, root: String)

/**
 * Information used to configure Android facet in IDEA.
 * Currently only android-sdk-plugin is supported.
 */
case class AndroidData(targetVersion: String,
                       manifest: File,
                       apk: File,
                       res: File,
                       assets: File,
                       gen: File,
                       libs: File,
                       isLibrary: Boolean,
                       proguardConfig: Seq[String],
                       apklibs: Seq[ApkLib],
                       aars: Seq[Aar])

case class Aar(name: String, project: ProjectData)

/**
 * Information about certain apklib used in Android project
 */
case class ApkLib(name: String, base: File, manifest: File, sources: File, resources: File, libs: File, gen: File)

/**
 * List of parameters specific to Play projects
 */
case class Play2Data(playVersion: Option[String],
                     templatesImports: Seq[String],
                     routesImports: Seq[String],
                     confDirectory: Option[File],
                     sourceDirectory: File)
