package org.jetbrains.sbt.structure

import java.io.File
import java.net.URI

import org.jetbrains.sbt.structure.DataSerializers._
import org.jetbrains.sbt.structure.XmlSerializer._

import scala.xml._
import scala.language.implicitConversions

//noinspection LanguageFeature
private object Helpers {
  class RichFile(file: File) {
    def path: String = file.getCanonicalPath.stripSuffix("/").stripSuffix("\\")
  }

  class RichNode(node: Node) {
    def !(name: String): Node = node \ name match {
      case Seq() => throw new RuntimeException("None of " + name + " nodes is found in " + node)
      case Seq(child) => child
      case _ => throw new RuntimeException("Multiple " + name + " nodes are found in " + node)
    }
  }

  class RicherString(string: String) {
    def canonIfFile: String =
      (try { Option(string.file) }
      catch { case x: java.io.IOException => None })
        .filter(_.exists)
        .map(_.path )
        .getOrElse(string)

    // converting a string to a file like this can be unsafe for characters that are illegal in some environments
    def file: File =
      new File(string.trim).getCanonicalFile

    def uri: URI =
      canonUri(new URI(string.replace("\\", "/"))) // handle windows separators
  }

  implicit def file2richFile(file: File): RichFile =
    new RichFile(file)

  implicit def node2richNode(node: Node): RichNode =
    new RichNode(node)

  implicit def string2RicherString(string: String): RicherString =
    new RicherString(string)

  implicit def seqToImmutableSeq[T](seq: scala.collection.Seq[T]): scala.collection.immutable.Seq[T] = {
    val builder = scala.collection.immutable.Seq.newBuilder[T]
    builder ++= seq
    builder.result
  }

  // sbt provides bad uris with spaces for local resolvers
  // https://youtrack.jetbrains.com/issue/SCL-12292
  def fixUri(path: String): URI = {
    val filePrefix = "file:/"
    if (path.startsWith(filePrefix))
      path.stripPrefix(filePrefix).file.toURI
    else new URI(path)
  }

  def canonUri(uri: URI): URI = {
    val uri1 =
      if (uri.getScheme == "file")
        new File(uri).getCanonicalFile.toURI
      else
        uri
    uri1.normalize()
  }
}

trait DataSerializers {

  import Helpers._

  implicit val buildDataSerializer: XmlSerializer[BuildData] = new XmlSerializer[BuildData] {
    override def serialize(what: BuildData): Elem =
      <build>
        <uri>{what.uri.toString}</uri>
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
      val uri     = (what \ "uri").map(_.text.uri).head
      val imports = (what \ ImportElementName).map(_.text)
      val classes = (what \ ClassesElementName).map(e => e.text.file)
      val docs    = (what \ DocsElementName).map(e => e.text.file)
      val sources = (what \ SourcesElementName).map(e => e.text.file)
      Right(BuildData(uri, imports, classes, docs, sources))
    }
  }

  implicit val configurationDataSerializer: XmlSerializer[ConfigurationData] = new XmlSerializer[ConfigurationData] {
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
      val excludes  = (what \ "exclude").map(e => e.text.file)
      val classes   = (what ! "classes").text.file

      Right(ConfigurationData(id, sources, resources, excludes, classes))
    }

    private def parseDirectory(node: Node): DirectoryData = {
      val managed = (node \ "@managed").headOption.exists(_.text.toBoolean)
      DirectoryData(node.text.file, managed)
    }

    private def format(b: Boolean) = if (b) Some(Text("true")) else None
  }

  implicit val javaDataSerializer: XmlSerializer[JavaData] = new XmlSerializer[JavaData] {
    override def serialize(what: JavaData): Elem =
      <java>
        {what.home.toSeq.map { file =>
        <home>{file.path}</home>
      }}
        { what.options.sortBy(_.configuration.name).map(_.serialize) }
      </java>

    override def deserialize(what: Node): Either[Throwable,JavaData] = {
      val home    = (what \ "home").headOption.map(e => e.text.file)
      val options = (what \ "compilerOptions").deserialize[CompilerOptions]
      Right(JavaData(home, options))
    }
  }

  implicit val scalaDataSerializer: XmlSerializer[ScalaData] = new XmlSerializer[ScalaData] {
    override def serialize(what: ScalaData): Elem =
      <scala>
        {Some(what.organization).filterNot(_ == DefaultScalaOrganization).toSeq.map { organization =>
        <organization>{organization}</organization>
        }}
        <version>{what.version}</version>

        <libraryJars>{ what.libraryJars.map  { jar => <jar>{jar.path}</jar> }}</libraryJars>
        <compilerJars>{ what.compilerJars.map { jar => <jar>{jar.path}</jar> }}</compilerJars>
        <extraJars>{ what.extraJars.map    { jar => <jar>{jar.path}</jar> }}</extraJars>

        { what.compilerBridgeBinaryJar.toSeq.map { jar => <compilerBridgeBinaryJar>{jar.path}</compilerBridgeBinaryJar>} }

        { what.options.sortBy(_.configuration.name).map(_.serialize) }
      </scala>

    override def deserialize(what: Node): Either[Throwable,ScalaData] = {
      val organization = (what \ "organization").headOption.map(_.text).getOrElse(DefaultScalaOrganization)
      val version = (what \ "version").text

      val libraryJars = (what \ "libraryJars" \ "jar").map(_.text.file)
      val compilerJars = (what \ "compilerJars"\ "jar").map(_.text.file)
      val extraJars = (what \ "extraJars"\ "jar").map(_.text.file)
      val compilerBridgeBinaryJar = (what \ "compilerBridgeBinaryJar").headOption.map(_.text.file)

      val options = (what \ "compilerOptions").deserialize[CompilerOptions]
      Right(ScalaData(
        organization,
        version,
        libraryJars,
        compilerJars,
        extraJars,
        compilerBridgeBinaryJar,
        options
      ))
    }
  }

  implicit val projectDependenciesSerializer: XmlSerializer[Dependencies[ProjectDependencyData]] = new XmlSerializer[Dependencies[ProjectDependencyData]] {
    override def serialize(what: Dependencies[ProjectDependencyData]): Elem =
      <projects>
        <forTest>
          {what.forTest.sortBy(_.project).map(_.serialize)}
        </forTest>
        <forProduction>
          {what.forProduction.sortBy(_.project).map(_.serialize)}
        </forProduction>
      </projects>

    override def deserialize(what: Node): Either[Throwable, Dependencies[ProjectDependencyData]] = {
      val testDependencies = (what \ "forTest" \ "project").deserialize[ProjectDependencyData]
      val compileDependencies = (what \ "forProduction" \ "project").deserialize[ProjectDependencyData]
      Right(Dependencies(compileDependencies, testDependencies))
    }
  }

  implicit val projectDependencySerializer: XmlSerializer[ProjectDependencyData] = new XmlSerializer[ProjectDependencyData] {
    override def serialize(what: ProjectDependencyData): Elem = {
      val configurations = what.configurations.mkString(";")
      what.buildURI.map { buildURI =>
        <project buildURI={buildURI.toString} configurations={configurations}>{what.project}</project>
      } getOrElse {
        <project configurations={configurations}>{what.project}</project>
      }
    }

    override def deserialize(what: Node): Either[Throwable, ProjectDependencyData] = {
      val project = what.text
      val buildURI = (what \ "@buildURI").text.uri
      val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
      Right(ProjectDependencyData(project, Some(buildURI), configurations.getOrElse(Seq.empty)))
    }
  }

  implicit val moduleIdentifierSerializer: XmlSerializer[ModuleIdentifier] = new XmlSerializer[ModuleIdentifier] {
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

  implicit val moduleDependenciesSerializer: XmlSerializer[Dependencies[ModuleDependencyData]] = new XmlSerializer[Dependencies[ModuleDependencyData]] {
    override def serialize(what: Dependencies[ModuleDependencyData]): Elem =
      <modules>
        <forTest>
          {what.forTest.map(_.serialize)}
        </forTest>
        <forProduction>
          {what.forProduction.map(_.serialize)}
        </forProduction>
      </modules>

    override def deserialize(what: Node): Either[Throwable, Dependencies[ModuleDependencyData]] = {
      val testDependencies = (what \ "forTest" \ "module").deserialize[ModuleDependencyData]
      val compileDependencies = (what \ "forProduction" \ "module").deserialize[ModuleDependencyData]
      Right(Dependencies(compileDependencies, testDependencies))
    }
  }

  implicit val moduleDependencyDataSerializer: XmlSerializer[ModuleDependencyData] = new XmlSerializer[ModuleDependencyData] {
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

  implicit val jarDependenciesSerializer: XmlSerializer[Dependencies[JarDependencyData]] = new XmlSerializer[Dependencies[JarDependencyData]] {
    override def serialize(what: Dependencies[JarDependencyData]): Elem =
      <jars>
        <forTest>
          {what.forTest.map(_.serialize)}
        </forTest>
        <forProduction>
          {what.forProduction.map(_.serialize)}
        </forProduction>
      </jars>

    override def deserialize(what: Node): Either[Throwable, Dependencies[JarDependencyData]] = {
      val testDependencies = (what \ "forTest" \ "jar").deserialize[JarDependencyData]
      val compileDependencies = (what \ "forProduction" \ "jar").deserialize[JarDependencyData]
      Right(Dependencies(compileDependencies, testDependencies))
    }
  }

  implicit val jarDependencyDataSerializer: XmlSerializer[JarDependencyData] = new XmlSerializer[JarDependencyData] {
    override def serialize(what: JarDependencyData): Elem =
      <jar configurations={what.configurations.mkString(";")}>{what.file.path}</jar>

    override def deserialize(what: Node): Either[Throwable,JarDependencyData] = {
      val jar = what.text.file
      val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
      Right(JarDependencyData(jar, configurations.getOrElse(Seq.empty)))
    }
  }

  implicit val dependencyDataSerializer: XmlSerializer[DependencyData] = new XmlSerializer[DependencyData] {
    override def serialize(what: DependencyData): Elem =
      <dependencies>
        {what.projects.serialize}
        {what.modules.serialize}
        {what.jars.serialize}
      </dependencies>

    override def deserialize(what: Node): Either[Throwable,DependencyData] = {
      for {
        projects <- (what \ "projects").deserializeOne[Dependencies[ProjectDependencyData]].right
        modules <- (what \ "modules").deserializeOne[Dependencies[ModuleDependencyData]].right
        jars <- (what \ "jars").deserializeOne[Dependencies[JarDependencyData]].right
      } yield {
        DependencyData(projects, modules, jars)
      }
    }
  }

  implicit val compilerOptionsSerializer: XmlSerializer[CompilerOptions] = new XmlSerializer[CompilerOptions] {
    override def serialize(what: CompilerOptions): Elem = {
      <compilerOptions>
        <configuration>{what.configuration}</configuration>
        {what.options.map(option => <option>{option}</option>)}
      </compilerOptions>
    }

    override def deserialize(what: Node): Either[Throwable,CompilerOptions] = {
      val configuration = (what \ "configuration").text
      val options = (what \ "option").map(_.text)
      Right(CompilerOptions(Configuration(configuration), options))
    }
  }

  implicit val moduleDataSerializer: XmlSerializer[ModuleData] = new XmlSerializer[ModuleData] {
    override def serialize(what: ModuleData): Elem = {
      val artifacts =
        what.binaries.toSeq.sorted.map(it => <jar>{it.path}</jar>) ++
          what.docs.toSeq.sorted.map(it => <doc>{it.path}</doc>) ++
          what.sources.toSeq.sorted.map(it => <src>{it.path}</src>)
      what.id.serialize.copy(child = artifacts)
    }

    override def deserialize(what: Node): Either[Throwable,ModuleData] =
      what.deserialize[ModuleIdentifier].fold(exc => Left(exc), { id =>
        val binaries  = (what \ "jar").map(n => n.text.file).toSet
        val docs      = (what \ "doc").map(n => n.text.file).toSet
        val sources   = (what \ "src").map(n => n.text.file).toSet
        Right(ModuleData(id, binaries, docs, sources))
      })
  }

  implicit val repositoryDataSerializer: XmlSerializer[RepositoryData] = new XmlSerializer[RepositoryData] {
    override def serialize(what: RepositoryData): Elem =
      <repository>
        {what.modules.map(_.serialize)}
      </repository>

    override def deserialize(what: Node): Either[Throwable,RepositoryData] = {
      val modules = (what \ "module").deserialize[ModuleData]
      Right(RepositoryData(modules))
    }
  }

  implicit val resolverDataSerializer: XmlSerializer[ResolverData] = new XmlSerializer[ResolverData] {
    override def serialize(what: ResolverData): Elem = {
      val uri = fixUri(what.root)
      <resolver name={what.name} root={canonUri(uri).toString}/>
    }

    override def deserialize(what: Node): Either[Throwable,ResolverData] = {
      val name = (what \ "@name").text
      val root = (what \ "@root").text
      val canonRoot = canonUri(root.uri).toString
      Right(ResolverData(name, canonRoot))
    }
  }

  implicit val settingDataSerializer: XmlSerializer[SettingData] = new XmlSerializer[SettingData] {
    override def serialize(what: SettingData): Elem = {
      <setting>
        <label>{what.label}</label>
        { what.description.toSeq.map { description => <description>{description}</description> } }
        <rank>{what.rank}</rank>
        { what.stringValue.toSeq.map { value => <value>{value}</value> } }
      </setting>
    }

    override def deserialize(what: Node): Either[Throwable, SettingData] = {
      val label = (what \ "label").text
      val description = (what \ "description").headOption.map(_.text)
      val rank = (what \ "rank").text.toInt
      val stringValue = (what \ "value").headOption.map(_.text)

      Right(SettingData(label, description, rank, stringValue))
    }
  }

  implicit val commandDataSerializer: XmlSerializer[CommandData] = new XmlSerializer[CommandData] {
    override def serialize(what: CommandData): Elem = {
      <command>
        <name>{what.name}</name>
        { what.help.map { case (cmd,description) =>
          <help>
            <cmd>{cmd}</cmd>
            <desc>{description}</desc>
          </help>
        }}
      </command>
    }

    override def deserialize(what: Node): Either[Throwable, CommandData] = {
      val name = (what \ "name").text
      val help = (what \ "help").map { helpNode =>
        val cmd = (helpNode \ "cmd").text
        val description = (helpNode \ "desc").text
        (cmd, description)
      }
      Right(CommandData(name, help))
    }
  }

  implicit val taskDataSerializer: XmlSerializer[TaskData] = new XmlSerializer[TaskData] {
    override def serialize(what: TaskData): Elem = {
      <task>
        <label>{what.label}</label>
        { what.description.toSeq.map { description =>
        <description>{description}</description> }
        }
        <rank>{what.rank}</rank>
      </task>
    }

    override def deserialize(what: Node): Either[Throwable, TaskData] = {
      val label = (what \ "label").text
      val description = (what \ "description").headOption.map(_.text)
      val rank = (what \ "rank").text.toInt

      Right(TaskData(label, description, rank))
    }
  }

  implicit val play2DataSerializer: XmlSerializer[Play2Data] = new XmlSerializer[Play2Data] {
    override def serialize(what: Play2Data): Elem =
      <play2>
        {what.playVersion.toSeq.map(ver => <version>{ver}</version> )}
        <templatesImports>
          {what.templatesImports.map(imp => <import>{imp}</import>)}
        </templatesImports>
        <routesImports>
          {what.routesImports.map(imp => <import>{imp}</import>)}
        </routesImports>
        {what.confDirectory.toSeq.map(dir => <confDirectory>{dir.path}</confDirectory>)}
        <sourceDirectory>{what.sourceDirectory.path}</sourceDirectory>
      </play2>

    override def deserialize(what: Node): Either[Throwable,Play2Data] = {
      val playVersion       = (what \ "version").map(_.text).headOption
      val templatesImports  = (what \ "templatesImports" \ "import").map(_.text)
      val routesImports     = (what \ "routesImports" \ "import").map(_.text)
      val confDirectory     = (what \ "confDirectory").map(_.text).headOption
      val sourceDirectory   = (what ! "sourceDirectory").text
      Right(Play2Data(playVersion, templatesImports, routesImports, confDirectory.map(_.file), sourceDirectory.file))
    }
  }

  implicit val projectDataSerializer: XmlSerializer[ProjectData] = new XmlSerializer[ProjectData] {
    override def serialize(what: ProjectData): Elem =
      <project>
        <id>{what.id}</id>
        <buildURI>{what.buildURI}</buildURI>
        <name>{what.name}</name>
        <organization>{what.organization}</organization>
        <version>{what.version}</version>
        <base>{what.base.path}</base>
        {what.packagePrefix.map(prefix => <packagePrefix>{prefix}</packagePrefix>).toSeq}
        {what.basePackages.map(name => <basePackage>{name}</basePackage>)}
        <target>{what.target.path}</target>
        {what.java.map(_.serialize).toSeq}
        {what.scala.map(_.serialize).toSeq}
        <compileOrder>{what.compileOrder}</compileOrder>
        {what.configurations.sortBy(_.id).map(_.serialize)}
        {what.dependencies.serialize}
        {what.resolvers.map(_.serialize).toSeq}
        {what.play2.map(_.serialize).toSeq}
        {what.settings.map(_.serialize)}
        {what.tasks.map(_.serialize)}
        {what.commands.map(_.serialize)}
        {what.testSourceDirectories.map(dir => <testSourceDir>{dir.path}</testSourceDir>)}
        {what.mainSourceDirectories.map(dir => <mainSourceDir>{dir.path}</mainSourceDir>)}
      </project>

    override def deserialize(what: Node): Either[Throwable,ProjectData] = {
      val id = (what \ "id").text
      val buildURI = (what \ "buildURI").text.uri
      val name = (what \ "name").text
      val organization = (what \ "organization").text
      val version = (what \ "version").text
      val base = (what \ "base").text.file
      val testSourceDirectories = (what \ "testSourceDir").map(_.text.file)
      val mainSourceDirectories = (what \ "mainSourceDir").map(_.text.file)
      val packagePrefix = (what \ "packagePrefix").headOption.map(_.text)
      val basePackages = (what \ "basePackage").map(_.text)
      val target = (what \ "target").text.file

      val configurations = (what \ "configuration").deserialize[ConfigurationData]
      val java = (what \ "java").deserialize[JavaData].headOption
      val scala = (what \ "scala").deserialize[ScalaData].headOption
      val compileOrder = (what \ "compileOrder").text
      val resolvers = (what \ "resolver").deserialize[ResolverData].toSet
      val play2 = (what \ "play2").deserialize[Play2Data].headOption

      val settings = (what \ "setting").deserialize[SettingData]
      val tasks = (what \ "task").deserialize[TaskData]
      val commands = (what \ "command").deserialize[CommandData]

      val tryDeps = (what \ "dependencies").deserializeOne[DependencyData]
      tryDeps.right.map { dependencies =>
        ProjectData(id, buildURI, name, organization, version, base, packagePrefix, basePackages,
          target, configurations, java, scala, compileOrder,
          dependencies, resolvers, play2, settings, tasks, commands, mainSourceDirectories, testSourceDirectories)
      }

    }
  }

  implicit val structureDataSerializer: XmlSerializer[StructureData] = new XmlSerializer[StructureData] {
    override def serialize(what: StructureData): Elem =
      <structure sbt={what.sbtVersion}>
        {what.builds.sortBy(_.uri).map(_.serialize)}
        {what.projects.sortBy(_.base).map(project => project.serialize)}
        {what.repository.map(_.serialize).toSeq}
        {what.localCachePath.map(path => <localCachePath>{path.path}</localCachePath>).toSeq}
      </structure>

    override def deserialize(what: Node): Either[Throwable,StructureData] = {
      val sbtVersion = (what \ "@sbt").text
      val builds = (what \ "build").deserialize[BuildData]
      val projects = (what \ "project").deserialize[ProjectData]
      val repository = (what \ "repository").deserialize[RepositoryData].headOption
      val localCachePath = (what \ "localCachePath").headOption.map(_.text.file)

      if (sbtVersion.isEmpty)
        Left(new Error("<structure> property 'sbt' is empty"))
      else
        Right(StructureData(sbtVersion, builds, projects, repository, localCachePath))
    }
  }
}

private[sbt] object DataSerializers {
  private val DefaultScalaOrganization = "org.scala-lang"

  val ImportElementName = "import"
  val ClassesElementName = "classes"
  val DocsElementName = "docs"
  val SourcesElementName = "sources"
}
