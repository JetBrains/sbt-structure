package org.jetbrains.sbt

import java.io.File

//import scala.language.implicitConversions
//import scala.language.reflectiveCalls

import org.jetbrains.sbt.XmlSerializer._

import scala.xml._


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
  val IntegrationTest = Configuration("it")
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
case class BuildData(imports: Seq[String],
                     classes: Seq[File],
                     docs: Seq[File],
                     sources: Seq[File])

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
                       manifestPath: String,
                       apkPath: String,
                       resPath: String,
                       assetsPath: String,
                       genPath: String,
                       libsPath: String,
                       isLibrary: Boolean,
                       proguardConfig: Seq[String])

/**
 * List of parameters specific to Play projects
 */
case class Play2Data(keys: Seq[Play2Key])

// TODO: maybe escape values?
sealed trait PlayValue
case class PlayString(value: String) extends PlayValue
case class PlaySeqString(value: Seq[String]) extends PlayValue

case class Play2Key(name: String, values: Map[String, PlayValue])


// WARN: order of these objects is important because of implicits resolution

private object Helper {
  class RichFile(file: File) {
    def path = file.getCanonicalPath.replace('\\', '/')
  }

  class RichNode(node: Node) {
    def !(name: String): Node = (node \ name) match {
      case Seq() => throw new RuntimeException("None of " + name + " nodes is found in " + node)
      case Seq(child) => child
      case _ => throw new RuntimeException("Multiple " + name + " nodes are found in " + node)
    }
  }

  implicit def file2richFile(file: File): RichFile =
    new RichFile(file)

  implicit def node2richNode(node: Node): RichNode =
    new RichNode(node)

  def file(path: String) =
    new File(path.trim)
}

import org.jetbrains.sbt.Helper._

object BuildData {
  implicit val serializer = new XmlSerializer[BuildData] {
    override def serialize(what: BuildData): Elem =
      <build>
        {what.imports.map { it =>
        <import>{it}</import>
      }}{what.classes.map(_.path).sorted.map { it =>
        <classes>{it}</classes>
      }}{what.docs.map { it =>
        <docs>{it.path}</docs>
      }}{what.sources.map { it =>
        <sources>{it.path}</sources>
      }}
      </build>

    override def deserialize(what: Node): Either[Throwable,BuildData] = {
      val imports = (what \ "import").map(_.text)
      val classes = (what \ "classes").map(e => file(e.text))
      val docs    = (what \ "docs").map(e => file(e.text))
      val sources = (what \ "sources").map(e => file(e.text))
      Right(BuildData(imports, classes, docs, sources))
    }
  }
}

object ConfigurationData {
  implicit val serializer = new XmlSerializer[ConfigurationData] {
    override def serialize(what: ConfigurationData): Elem =
      <configuration id={what.id}>
        {what.sources.sortBy(it => (it.managed, it.file)).map { directory =>
        <sources managed={format(directory.managed)}>{directory.file.path}</sources>
        }}
        {what.resources.sortBy(it => (it.managed, it.file)).map { directory =>
        <resources managed={format(directory.managed)}>{directory.file.path}</resources>
        }}
        {what.excludes.sorted.map { directory =>
        <exclude>{directory.path}</exclude>
        }}
        <classes>{what.classes.path}</classes>
      </configuration>

    override def deserialize(what: Node): Either[Throwable,ConfigurationData] = {
      val id        = (what \ "@id").text
      val sources   = (what \ "sources").map(parseDirectory)
      val resources = (what \ "resources").map(parseDirectory)
      val excludes  = (what \ "exclude").map(e => file(e.text))
      val classes   = file((what ! "classes").text)

      Right(ConfigurationData(id, sources, resources, excludes, classes))
    }

    private def parseDirectory(node: Node): DirectoryData = {
      val managed = (node \ "@managed").headOption.exists(_.text.toBoolean)
      DirectoryData(file(node.text), managed)
    }

    private def format(b: Boolean) = if (b) Some(Text("true")) else None
  }
}

object JavaData {
  implicit val serializer = new XmlSerializer[JavaData] {
    override def serialize(what: JavaData): Elem =
      <java>
        {what.home.toSeq.map { file =>
        <home>{file.path}</home>
        }}
        {what.options.map { option =>
        <option>{option}</option>
        }}
      </java>

    override def deserialize(what: Node): Either[Throwable,JavaData] = {
      val home    = (what \ "home").headOption.map(e => file(e.text))
      val options = (what \ "option").map(_.text)
      Right(JavaData(home, options))
    }
  }
}

object ScalaData {
  implicit val serializer = new XmlSerializer[ScalaData] {
    override def serialize(what: ScalaData): Elem =
      <scala>
        <version>{what.version}</version>
        <library>{what.libraryJar.path}</library>
        <compiler>{what.compilerJar.path}</compiler>
        {what.extraJars.map { jar =>
        <extra>{jar.path}</extra>
        }}
        {what.options.map { option =>
        <option>{option}</option>
        }}
      </scala>

    override def deserialize(what: Node): Either[Throwable,ScalaData] = {
      val version  = (what \ "version").text
      val library  = file((what \ "library").text)
      val compiler = file((what \ "compiler").text)
      val extra    = (what \ "extra").map(e => file(e.text))
      val options  = (what \ "option").map(_.text)
      Right(ScalaData(version, library, compiler, extra, options))
    }
  }
}

object ProjectDependencyData {
  implicit val serializer = new XmlSerializer[ProjectDependencyData] {
    override def serialize(what: ProjectDependencyData): Elem =
      <project configurations={what.configuration.mkString(";")}>{what.project}</project>

    override def deserialize(what: Node): Either[Throwable,ProjectDependencyData] = {
      val project = what.text
      val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
      Right(ProjectDependencyData(project, configurations.getOrElse(Seq.empty)))
    }
  }
}

object ModuleIdentifier {
  implicit val serializer = new XmlSerializer[ModuleIdentifier] {
    override def serialize(what: ModuleIdentifier): Elem =
        <module organization={what.organization}
                name={what.name}
                revision={what.revision}
                artifactType={what.artifactType}
                classifier={what.classifier}/>

    override def deserialize(what: Node): Either[Throwable,ModuleIdentifier] = {
      val organization  = (what \ "@organization").text
      val name          = (what \ "@name").text
      val revision      = (what \ "@revision").text
      val artifactType  = (what \ "@artifactType").text
      val classifier    = (what \ "@classifier").text
      Right(ModuleIdentifier(organization, name, revision, artifactType, classifier))
    }
  }
}

object ModuleDependencyData {
  implicit val serializer = new XmlSerializer[ModuleDependencyData] {
    override def serialize(what: ModuleDependencyData): Elem = {
      val elem = what.id.serialize
      elem % Attribute("configurations", Text(what.configurations.mkString(";")), Null)
    }

    override def deserialize(what: Node): Either[Throwable,ModuleDependencyData] = {
      what.deserialize[ModuleIdentifier].fold(exc => Left(exc), { id =>
        val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
        Right(ModuleDependencyData(id, configurations.getOrElse(Seq.empty)))
      })
    }
  }
}

object JarDependencyData {
  implicit val serializer = new XmlSerializer[JarDependencyData] {
    override def serialize(what: JarDependencyData): Elem =
      <jar configurations={what.configurations.mkString(";")}>{what.file.path}</jar>

    override def deserialize(what: Node): Either[Throwable,JarDependencyData] = {
      val jar = file(what.text)
      val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
      Right(JarDependencyData(jar, configurations.getOrElse(Seq.empty)))
    }
  }
}

object DependencyData {
  implicit val serializer = new XmlSerializer[DependencyData] {
    override def serialize(what: DependencyData): Elem =
      <dependencies>
        {what.projects.sortBy(_.project).map(_.serialize)}
        {what.modules.sortBy(_.id.key).map(_.serialize)}
        {what.jars.sortBy(_.file).map(_.serialize)}
      </dependencies>

    override def deserialize(what: Node): Either[Throwable,DependencyData] = {
      val projects = (what \ "project").deserialize[ProjectDependencyData]
      val modules = (what \ "module").deserialize[ModuleDependencyData]
      val jars = (what \ "jar").deserialize[JarDependencyData]
      Right(DependencyData(projects, modules, jars))
    }
  }
}

object ModuleData {
  implicit val serializer = new XmlSerializer[ModuleData] {
    override def serialize(what: ModuleData): Elem = {
      val artifacts =
        what.binaries.toSeq.sorted.map(it => <jar>{it.path}</jar>) ++
        what.docs.toSeq.sorted.map(it => <doc>{it.path}</doc>) ++
        what.sources.toSeq.sorted.map(it => <src>{it.path}</src>)
      what.id.serialize.copy(child = artifacts.toSeq)
    }

    override def deserialize(what: Node): Either[Throwable,ModuleData] =
      what.deserialize[ModuleIdentifier].fold(exc => Left(exc), { id =>
        val binaries  = (what \ "jar").map(n => file(n.text)).toSet
        val docs      = (what \ "doc").map(n => file(n.text)).toSet
        val sources   = (what \ "src").map(n => file(n.text)).toSet
        Right(ModuleData(id, binaries, docs, sources))
      })
  }
}

object RepositoryData {
  implicit val serializer = new XmlSerializer[RepositoryData] {
    override def serialize(what: RepositoryData): Elem =
      <repository>
        {what.modules.sortBy(_.id.key).map(_.serialize)}
      </repository>

    override def deserialize(what: Node): Either[Throwable,RepositoryData] = {
      val modules = (what \ "module").deserialize[ModuleData]
      Right(RepositoryData(modules))
    }
  }
}

object ResolverData {
  implicit val serializer = new XmlSerializer[ResolverData] {
    override def serialize(what: ResolverData): Elem =
        <resolver name={what.name} root={what.root}/>

    override def deserialize(what: Node): Either[Throwable,ResolverData] = {
      val name = (what \ "@name").text
      val root = (what \ "@root").text
      Right(ResolverData(name, root))
    }
  }
}

object AndroidData {
  implicit val serializer = new XmlSerializer[AndroidData] {
    override def serialize(what: AndroidData): Elem =
      <android>
        <version>{what.targetVersion}</version>
        <manifest>{what.manifestPath}</manifest>
        <resources>{what.resPath}</resources>
        <assets>{what.assetsPath}</assets>
        <generatedFiles>{what.genPath}</generatedFiles>
        <nativeLibs>{what.libsPath}</nativeLibs>
        <apk>{what.apkPath}</apk>
        <isLibrary>{what.isLibrary}</isLibrary>
        <proguard>{what.proguardConfig.map { opt =>
          <option>{opt}</option>
        }}
        </proguard>
      </android>

    override def deserialize(what: Node): Either[Throwable,AndroidData] = {
      val version         = (what \ "version").text
      val manifestFile    = (what \ "manifest").text
      val apkPath         = (what \ "apk").text
      val resPath         = (what \ "resources").text
      val assetsPath      = (what \ "assets").text
      val genPath         = (what \ "generatedFiles").text
      val libsPath        = (what \ "nativeLibs").text
      val isLibrary       = (what \ "isLibrary").text.toBoolean
      val proguardConfig  = (what \ "proguard" \ "option").map(_.text)
      Right(AndroidData(version, manifestFile, apkPath, resPath, assetsPath, genPath, libsPath, isLibrary, proguardConfig))
    }
  }
}

object PlayValue {
  implicit val serializer = new XmlSerializer[PlayValue] {
    override def serialize(what: PlayValue): Elem = what match {
      case PlayString(str) =>
        <value type="string">{str}</value>
      case PlaySeqString(strings) =>
        <value type="stringSeq">{strings.map(s => <entry>{s}</entry>)}</value>
    }

    override def deserialize(what: Node): Either[Throwable, PlayValue] = (what \ "@type").text match {
      case "string" =>
        Right(PlayString(what.text))
      case "stringSeq" =>
        Right(PlaySeqString((what \ "entry").map(_.text)))
      case t =>
        Left(new Error("Unknown type of value: " + t))
    }
  }
}

object Play2Key {
  implicit val serializer = new XmlSerializer[Play2Key] {
    override def serialize(what: Play2Key): Elem =
      <key name={what.name}>
        {what.values.map { case (projectName, value) =>
        <in project={projectName}>
          {value.serialize}
        </in>
        }}
      </key>

    override def deserialize(what: Node): Either[Throwable,Play2Key] = {
      val name = (what \ "@name").text
      val values = (what \ "in").flatMap { nodeValue =>
        val project = (nodeValue \ "@project").text
        val value = (nodeValue \ "value").deserializeOne[PlayValue].fold(_ => None, x => Some(x))
        value.map(v => project -> v)
      }
      Right(Play2Key(name, values.toMap))
    }
  }
}

object Play2Data {
  implicit val serializer = new XmlSerializer[Play2Data] {
    override def serialize(what: Play2Data): Elem =
      <playimps>
        {what.keys.map { k => k.serialize }}
      </playimps>

    override def deserialize(what: Node): Either[Throwable,Play2Data] =
      Right(Play2Data((what \ "key").deserialize[Play2Key]))
  }
}

object ProjectData {
  implicit val serializer = new XmlSerializer[ProjectData] {
    override def serialize(what: ProjectData): Elem =
      <project>
        <id>{what.id}</id>
        <name>{what.name}</name>
        <organization>{what.organization}</organization>
        <version>{what.version}</version>
        <base>{what.base.path}</base>
        {what.basePackages.map(name => <basePackage>{name}</basePackage>)}
        <target>{what.target.path}</target>
        {what.build.serialize}
        {what.java.map(_.serialize).toSeq}
        {what.scala.map(_.serialize).toSeq}
        {what.android.map(_.serialize).toSeq}
        {what.configurations.sortBy(_.id).map(_.serialize)}
        {what.dependencies.serialize}
        {what.resolvers.map(_.serialize).toSeq}
        {what.play2.map(_.serialize).toSeq}
      </project>

    override def deserialize(what: Node): Either[Throwable,ProjectData] = {
      val id = (what \ "id").text
      val name = (what \ "name").text
      val organization = (what \ "organization").text
      val version = (what \ "version").text
      val base = file((what \ "base").text)
      val basePackages = (what \ "basePackage").map(_.text)
      val target = file((what \ "target").text)

      val configurations = (what \ "configuration").deserialize[ConfigurationData]
      val java = (what \ "java").deserialize[JavaData].headOption
      val scala = (what \ "scala").deserialize[ScalaData].headOption
      val android = (what \ "android").deserialize[AndroidData].headOption
      val resolvers = (what \ "resolver").deserialize[ResolverData].toSet
      val play2 = (what \ "playimps").deserialize[Play2Data].headOption

      val tryBuildAndDeps = {
        val build = (what \ "build").deserializeOne[BuildData]
        val deps = (what \ "dependencies").deserializeOne[DependencyData]
        build.fold(exc => Left(exc), b => deps.fold(exc => Left(exc), d => Right((b, d))))
      }

      tryBuildAndDeps.fold(exc => Left(exc), { case(build, dependencies) =>
        Right(ProjectData(id, name, organization, version, base, basePackages,
          target, build, configurations, java, scala, android,
          dependencies, resolvers, play2))
      })
    }
  }
}

object StructureData {
  implicit val serializer = new XmlSerializer[StructureData] {
    override def serialize(what: StructureData): Elem =
      <structure sbt={what.sbtVersion}>
        {what.projects.sortBy(_.base).map(project => project.serialize)}
        {what.repository.map(_.serialize).toSeq}
        {what.localCachePath.map(path => <localCachePath>{path}</localCachePath>).toSeq}
      </structure>

    override def deserialize(what: Node): Either[Throwable,StructureData] = {
      val sbtVersion = (what \ "@sbt").text
      val projects = (what \ "project").deserialize[ProjectData]
      val repository = (what \ "repository").deserialize[RepositoryData].headOption
      val localCachePath = (what \ "localCachePath").headOption.map(_.text)

      if (sbtVersion.isEmpty)
        Left(new Error("<structure> property 'sbt' is empty"))
      else
        Right(StructureData(sbtVersion, projects, repository, localCachePath))
    }
  }
}
