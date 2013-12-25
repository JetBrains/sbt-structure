package org.jetbrains.sbt

import java.io.File
import scala.xml.{Text, Elem}
import FS._

/**
 * @author Pavel Fatin
 */
case class FS(home: File, base: Option[File] = None) {
  def withBase(base: File): FS = copy(base = Some(base))
}

object FS {
  val Home = "~/"
  val Base = ""

  private val Windows = System.getProperty("os.name").startsWith("Win")

  implicit def toRichFile(file: File)(implicit fs: FS) = new {
    def path: String = {
      val home = toPath(fs.home)
      val base = fs.base.map(toPath)

      val path = toPath(file)
      replace(base.map(it => replace(path, it + "/", Base)).getOrElse(path), home + "/", Home)
    }

    def absolutePath: String = toPath(file)
  }

  private def replace(path: String, root: String, replacement: String) = {
    val (target, prefix) = if (Windows) (path.toLowerCase, root.toLowerCase) else (path, root)
    if (target.startsWith(prefix)) replacement + path.substring(root.length) else path
  }

  def toPath(file: File) = file.getAbsolutePath.replace('\\', '/')
}

case class StructureData(sbt: String, scala: ScalaData, projects: Seq[ProjectData], repository: Option[RepositoryData]) {
  def toXML(home: File): Elem = {
    val fs = new FS(home)

    <structure sbt={sbt}>
      {projects.sortBy(_.base).map(project => project.toXML(fs.withBase(project.base)))}
      {repository.map(_.toXML(fs)).toSeq}
    </structure>
  }
}

case class ProjectData(id: String, name: String, organization: String, version: String, base: File, build: BuildData, configurations: Seq[ConfigurationData], java: Option[JavaData], scala: Option[ScalaData], dependencies: DependencyData) {
  def toXML(implicit fs: FS): Elem = {
    <project>
      <id>{id}</id>
      <name>{name}</name>
      <organization>{organization}</organization>
      <version>{version}</version>
      <base>{base.absolutePath}</base>
      {build.toXML}
      {java.map(_.toXML).toSeq}
      {scala.map(_.toXML).toSeq}
      {configurations.sortBy(_.id).map(_.toXML)}
      {dependencies.toXML}
    </project>
  }
}

case class BuildData(classpath: Seq[File], imports: Seq[String]) {
  def toXML(implicit fs: FS): Elem = {
    <build>
      {classpath.map(_.path).filter(_.startsWith(FS.Home)).sorted.map { it =>
        <classes>{it}</classes>
      }}
      {imports.map { it =>
        <import>{it}</import>
      }}
    </build>
  }
}

case class ConfigurationData(id: String, sources: Seq[DirectoryData], resources: Seq[DirectoryData], classes: File) {
  def toXML(implicit fs: FS): Elem = {
    <configuration id={id}>
      {sources.sortBy(it => (it.managed, it.file)).map { directory =>
          <sources managed={format(directory.managed)}>{directory.file.path}</sources>
      }}
      {resources.sortBy(it => (it.managed, it.file)).map { directory =>
        <resources managed={format(directory.managed)}>{directory.file.path}</resources>
      }}
      <classes>{classes.path}</classes>
    </configuration>
  }

  private def format(b: Boolean) = if (b) Some(Text("true")) else None
}

case class DirectoryData(file: File, managed: Boolean)

case class JavaData(home: Option[File], options: Seq[String]) {
  def toXML(implicit fs: FS): Elem = {
    <java>
      {home.toSeq.map { file =>
        <home>{file.path}</home>
      }}
      {options.map { option =>
        <option>{option}</option>
      }}
    </java>
  }
}

case class ScalaData(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File], options: Seq[String]) {
  def toXML(implicit fs: FS): Elem = {
    <scala>
      <version>{version}</version>
      <library>{libraryJar.path}</library>
      <compiler>{compilerJar.path}</compiler>
      {extraJars.map { jar =>
        <extra>{jar.path}</extra>
      }}
      {options.map { option =>
        <option>{option}</option>
      }}
    </scala>
  }
}

case class DependencyData(projects: Seq[ProjectDependencyData], modules: Seq[ModuleDependencyData], jars: Seq[File]) {
  def toXML(implicit fs: FS): Seq[Elem] = {
    projects.sortBy(_.project).map(_.toXML) ++
      modules.sortBy(_.id.key).map(_.toXML) ++
      jars.sorted.map(file => <jar>{file.path}</jar>)
  }
}

case class ProjectDependencyData(project: String, configuration: Option[String]) {
  def toXML: Elem = {
    <project configurations={configuration.map(Text(_))}>{project}</project>
  }
}

case class ModuleDependencyData(id: ModuleIdentifier, configurations: Option[String]) {
  def toXML: Elem = {
    <module organization={id.organization} name={id.name} revision={id.revision} configurations={configurations.map(Text(_))}/>
  }
}

case class ModuleIdentifier(organization: String, name: String, revision: String) {
  def toXML: Elem = {
    <module organization={organization} name={name} revision={revision}/>
  }

  def key: Iterable[String] = productIterator.toIterable.asInstanceOf[Iterable[String]]
}

case class ModuleData(id: ModuleIdentifier, binaries: Seq[File], docs: Seq[File], sources: Seq[File]) {
  def toXML(implicit fs: FS): Elem = {
    val artifacts =
      binaries.map(it => <jar>{it.path}</jar>) ++
      docs.map(it => <doc>{it.path}</doc>) ++
      sources.map(it => <src>{it.path}</src>)

    id.toXML.copy(child = artifacts)
  }
}

case class RepositoryData(modules: Seq[ModuleData]) {
  def toXML(implicit fs: FS): Elem = {
    <repository>
      {modules.sortBy(_.id.key).map(_.toXML)}
    </repository>
  }
}
