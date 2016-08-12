package org.jetbrains.sbt.structure

import java.io.File
import java.net.URI

import scala.xml._
import XmlSerializer._

import scala.util.Try

/**
  * @author Nikolay Obedin
  * @since 12/15/15.
  */
//noinspection LanguageFeature
private object Helpers {
  class RichFile(file: File) {
    def path = file.getCanonicalPath.stripSuffix("/").stripSuffix("\\")
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
      Try(string.file)
        .filter(_.exists)
        .map(_.path )
        .getOrElse(string)

    // converting a string to a file like this can be unsafe for characters that are illegal in some environments
    def file: File =
      new File(string.trim).getCanonicalFile
  }

  implicit def file2richFile(file: File): RichFile =
    new RichFile(file)

  implicit def node2richNode(node: Node): RichNode =
    new RichNode(node)

  implicit def string2RicherString(string: String): RicherString =
    new RicherString(string)

  def canonUri(uri: URI) =
    (if (uri.getScheme == "file")
      new File(uri).getCanonicalFile.toURI
    else uri).normalize()
}

trait DataSerializers {

  import Helpers._

  implicit val buildDataSerializer = new XmlSerializer[BuildData] {
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
      val classes = (what \ "classes").map(e => e.text.file)
      val docs    = (what \ "docs").map(e => e.text.file)
      val sources = (what \ "sources").map(e => e.text.file)
      Right(BuildData(imports, classes, docs, sources))
    }
  }

  implicit val configurationDataSerializer = new XmlSerializer[ConfigurationData] {
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

  implicit val javaDataSerializer = new XmlSerializer[JavaData] {
    override def serialize(what: JavaData): Elem =
      <java>
        {what.home.toSeq.map { file =>
        <home>{file.path}</home>
      }}
        {what.options.map { option =>
        <option>{option.canonIfFile}</option>
      }}
      </java>

    override def deserialize(what: Node): Either[Throwable,JavaData] = {
      val home    = (what \ "home").headOption.map(e => e.text.file)
      val options = (what \ "option").map(o => o.text.canonIfFile)
      Right(JavaData(home, options))
    }
  }

  implicit val scalaDataSerializer = new XmlSerializer[ScalaData] {
    override def serialize(what: ScalaData): Elem =
      <scala>
        <version>{what.version}</version>
        <library>{what.libraryJar.path}</library>
        <compiler>{what.compilerJar.path}</compiler>
        {what.extraJars.map { jar =>
        <extra>{jar.path}</extra>
      }}
        {what.options.map { option =>
        <option>{option.canonIfFile}</option>
      }}
      </scala>

    override def deserialize(what: Node): Either[Throwable,ScalaData] = {
      val version  = (what \ "version").text
      val library  = (what \ "library").text.file
      val compiler = (what \ "compiler").text.file
      val extra    = (what \ "extra").map(e => e.text.file)
      val options  = (what \ "option").map(o => o.text.canonIfFile)
      Right(ScalaData(version, library, compiler, extra, options))
    }
  }

  implicit val projectDependencySerializer = new XmlSerializer[ProjectDependencyData] {
    override def serialize(what: ProjectDependencyData): Elem =
      <project configurations={what.configuration.mkString(";")}>{what.project}</project>

    override def deserialize(what: Node): Either[Throwable,ProjectDependencyData] = {
      val project = what.text
      val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
      Right(ProjectDependencyData(project, configurations.getOrElse(Seq.empty)))
    }
  }

  implicit val moduleIdentifierSerializer = new XmlSerializer[ModuleIdentifier] {
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

  implicit val moduleDependencyDataSerializer = new XmlSerializer[ModuleDependencyData] {
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

  implicit val jarDependencyDataSerializer = new XmlSerializer[JarDependencyData] {
    override def serialize(what: JarDependencyData): Elem =
      <jar configurations={what.configurations.mkString(";")}>{what.file.path}</jar>

    override def deserialize(what: Node): Either[Throwable,JarDependencyData] = {
      val jar = what.text.file
      val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
      Right(JarDependencyData(jar, configurations.getOrElse(Seq.empty)))
    }
  }

  implicit val dependencyDataSerializer = new XmlSerializer[DependencyData] {
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

  implicit val moduleDataSerializer = new XmlSerializer[ModuleData] {
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

  implicit val repositoryDataSerializer = new XmlSerializer[RepositoryData] {
    override def serialize(what: RepositoryData): Elem =
      <repository>
        {what.modules.sortBy(_.id.key).map(_.serialize)}
      </repository>

    override def deserialize(what: Node): Either[Throwable,RepositoryData] = {
      val modules = (what \ "module").deserialize[ModuleData]
      Right(RepositoryData(modules))
    }
  }

  implicit val resolverDataSerializer = new XmlSerializer[ResolverData] {
    override def serialize(what: ResolverData): Elem = {
      val uri = new URI(what.root)
      <resolver name={what.name} root={canonUri(uri).toString}/>
    }

    override def deserialize(what: Node): Either[Throwable,ResolverData] = {
      val name = (what \ "@name").text
      val root = (what \ "@root").text
      val canonRoot = canonUri(new URI(root)).toString
      Right(ResolverData(name, canonRoot))
    }
  }

  implicit val apkLibSerializer = new XmlSerializer[ApkLib] {
    override def serialize(what: ApkLib): Elem =
      <apkLib name={what.name}>
        <manifest>{what.manifest.path}</manifest>
        <base>{what.base.path}</base>
        <sources>{what.sources.path}</sources>
        <resources>{what.resources.path}</resources>
        <libs>{what.libs.path}</libs>
        <gen>{what.gen.path}</gen>
      </apkLib>

    override def deserialize(what: Node): Either[Throwable, ApkLib] = {
      val name = (what \ "@name").text
      val base = (what \ "base").text
      val manifest = (what \ "manifest").text
      val sources = (what \ "sources").text
      val resources = (what \ "resources").text
      val libs = (what \ "libs").text
      val gen = (what \ "gen").text
      Right(ApkLib(name, base.file, manifest.file, sources.file, resources.file, libs.file, gen.file))
    }
  }

  implicit val androidDataSerializer = new XmlSerializer[AndroidData] {
    override def serialize(what: AndroidData): Elem =
      <android>
        <version>{what.targetVersion}</version>
        <manifest>{what.manifest.path}</manifest>
        <resources>{what.res.path}</resources>
        <assets>{what.assets.path}</assets>
        <generatedFiles>{what.gen.path}</generatedFiles>
        <nativeLibs>{what.libs.path}</nativeLibs>
        <apk>{what.apk.path}</apk>
        <isLibrary>{what.isLibrary}</isLibrary>
        <proguard>{what.proguardConfig.map { opt =>
          <option>{opt}</option>
        }}
        </proguard>
        {what.apklibs.map(_.serialize)}
      </android>

    override def deserialize(what: Node): Either[Throwable,AndroidData] = {
      val version         = (what \ "version").text
      val manifestPath    = (what \ "manifest").text
      val apkPath         = (what \ "apk").text
      val resPath         = (what \ "resources").text
      val assetsPath      = (what \ "assets").text
      val genPath         = (what \ "generatedFiles").text
      val libsPath        = (what \ "nativeLibs").text
      val isLibrary       = (what \ "isLibrary").text.toBoolean
      val proguardConfig  = (what \ "proguard" \ "option").map(_.text)
      val apklibs         = (what \ "apkLib").deserialize[ApkLib]
      Right(AndroidData(version, manifestPath.file, apkPath.file,
        resPath.file, assetsPath.file, genPath.file,
        libsPath.file, isLibrary, proguardConfig, apklibs, Nil))
    }
  }

  implicit val play2DataSerializer = new XmlSerializer[Play2Data] {
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

  implicit val projectDataSerializer = new XmlSerializer[ProjectData] {
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
      val base = (what \ "base").text.file
      val basePackages = (what \ "basePackage").map(_.text)
      val target = (what \ "target").text.file

      val configurations = (what \ "configuration").deserialize[ConfigurationData]
      val java = (what \ "java").deserialize[JavaData].headOption
      val scala = (what \ "scala").deserialize[ScalaData].headOption
      val android = (what \ "android").deserialize[AndroidData].headOption
      val resolvers = (what \ "resolver").deserialize[ResolverData].toSet
      val play2 = (what \ "play2").deserialize[Play2Data].headOption

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

  implicit val structureDataSerializer = new XmlSerializer[StructureData] {
    override def serialize(what: StructureData): Elem =
      <structure sbt={what.sbtVersion}>
        {what.projects.sortBy(_.base).map(project => project.serialize)}
        {what.repository.map(_.serialize).toSeq}
        {what.localCachePath.map(path => <localCachePath>{path.file.path}</localCachePath>).toSeq}
      </structure>

    override def deserialize(what: Node): Either[Throwable,StructureData] = {
      val sbtVersion = (what \ "@sbt").text
      val projects = (what \ "project").deserialize[ProjectData]
      val repository = (what \ "repository").deserialize[RepositoryData].headOption
      val localCachePath = (what \ "localCachePath").headOption.map(_.text.file.path)

      if (sbtVersion.isEmpty)
        Left(new Error("<structure> property 'sbt' is empty"))
      else
        Right(StructureData(sbtVersion, projects, repository, localCachePath))
    }
  }
}
