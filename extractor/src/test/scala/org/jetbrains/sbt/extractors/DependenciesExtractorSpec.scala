package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.Utilities._
import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import org.specs2.matcher.Matcher
import org.specs2.mutable._
import sbt._

class DependenciesExtractorSpec extends Specification {

  "DependenciesExtractor" should {
    "always extract build dependencies" in {
      val actual = new DependenciesExtractor(
        stubProject1, Some(toBuildDependencies(projectDependencies)), emptyClasspath, emptyClasspath, Nil, Nil
      ).extract
      val expected = DependencyData(toProjectDependencyData(projectDependencies), Nil, Nil)
      actual must beIdenticalTo(expected)
    }

    "always extract unmanaged dependencies" in {
      val actual = new DependenciesExtractor(
        stubProject1, None, toUnmanagedClasspath(unmanagedDependencies), emptyClasspath, Seq(sbt.Compile, sbt.Test), Seq(sbt.Test)
      ).extract
      val expected = DependencyData(Nil, Nil, toJarDependencyData(unmanagedDependencies))
      actual must beIdenticalTo(expected)
    }

    "extract managed dependencies when supplied" in {
      val actual = new DependenciesExtractor(
        stubProject1, None, emptyClasspath, toExternalDepenedncyClasspath(moduleDependencies), Seq(sbt.Compile, sbt.Test), Seq(sbt.Test)
      ).extract
      val expected = DependencyData(Nil, toModuleDependencyData(moduleDependencies), Nil)
      actual must beIdenticalTo(expected)
    }

    "merge custom test configurations in unmanaged and managed dependencies" in {
      val actual = new DependenciesExtractor(
        stubProject1, None,
        toUnmanagedClasspath(unmanagedDependenciesWithCustomConf),
        toExternalDepenedncyClasspath(moduleDependenciesWithCustomConf),
        Seq(sbt.Test, sbt.Compile, CustomConf), Seq(sbt.Test, CustomConf)
      ).extract
      val expected = DependencyData(Nil, toModuleDependencyData(moduleDependenciesWithCustomConf),
        toJarDependencyData(unmanagedDependenciesWithCustomConf))
      actual must beIdenticalTo(expected)
    }

    "extract managed dependency with classifier as different dependencies" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"
      val externalDependencyClasspath = Map(
        sbt.Compile -> Seq(
          attributedWith(file("foo.jar"))(moduleId, Artifact("foo")),
          attributedWith(file("foo-tests.jar"))(moduleId, Artifact("foo", "tests"))
        )
      )
      val actual = new DependenciesExtractor(stubProject1, None, emptyClasspath, externalDependencyClasspath.apply, Seq(sbt.Compile), Seq.empty).extract
      val expectedModules = Seq(toIdentifier(moduleId), toIdentifier(moduleId).copy(classifier = "tests"))
      val expected = DependencyData(Nil, expectedModules.map(it => ModuleDependencyData(it, Seq(jb.Configuration.Compile))), Nil)
      actual must beIdenticalTo(expected)
    }

    "merge (compile, test, runtime) -> compile in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"
      val externalDependencyClasspath = Map(
        sbt.Compile -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))),
        sbt.Test    -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))),
        sbt.Runtime -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo")))
      )
      val unmanagedClasspath = Map(
        sbt.Compile -> Seq(attributedWithNothing(existingFile("bar.jar"))),
        sbt.Test    -> Seq(attributedWithNothing(existingFile("bar.jar"))),
        sbt.Runtime -> Seq(attributedWithNothing(existingFile("bar.jar")))
      )
      val actual = new DependenciesExtractor(
        stubProject1, None, unmanagedClasspath.apply, externalDependencyClasspath.apply,
        Seq(sbt.Compile, sbt.Test, sbt.Runtime), Seq.empty).extract
      val expected = DependencyData(Nil,
        Seq(ModuleDependencyData(toIdentifier(moduleId), Seq(jb.Configuration.Compile))),
        Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile)))
      )
      actual must beIdenticalTo(expected)
    }

    "merge (compile, test) -> provided in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"
      val externalDependencyClasspath = Map(
        sbt.Compile -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))),
        sbt.Test    -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo")))
      )
      val unmanagedClasspath = Map(
        sbt.Compile -> Seq(attributedWithNothing(existingFile("bar.jar"))),
        sbt.Test    -> Seq(attributedWithNothing(existingFile("bar.jar")))
      )
      val actual = new DependenciesExtractor(
        stubProject1, None, unmanagedClasspath.apply, externalDependencyClasspath.apply,
        Seq(sbt.Compile, sbt.Test), Seq.empty).extract
      val expected = DependencyData(Nil,
        Seq(ModuleDependencyData(toIdentifier(moduleId), Seq(jb.Configuration.Provided))),
        Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Provided)))
      )
      actual must beIdenticalTo(expected)
    }
  }

  def beIdenticalTo(expected: DependencyData): Matcher[DependencyData] = { actual: DependencyData =>
    (actual.projects must containTheSameElementsAs(expected.projects)) and
      (actual.jars must containTheSameElementsAs(expected.jars)) and
      (actual.modules must containTheSameElementsAs(expected.modules))
  }

  def attributedWith(file: File)(moduleId: ModuleID, artifact: Artifact): Attributed[File] =
    Attributed(file)(AttributeMap.empty.put(sbt.Keys.moduleID.key, moduleId).put(sbt.Keys.artifact.key, artifact))

  def attributedWithNothing(file: File): Attributed[File] =
    Attributed(file)(AttributeMap.empty)

  def toIdentifier(moduleId: ModuleID): ModuleIdentifier =
    ModuleIdentifier(moduleId.organization, moduleId.name, moduleId.revision, Artifact.DefaultType, "")

  val stubProject1 = ProjectRef(file("/tmp/test-project"), "project-1")
  val stubProject2 = ProjectRef(file("/tmp/test-project"), "project-2")
  val emptyClasspath: sbt.Configuration => Keys.Classpath = _ => Nil

  val CustomConf = config("custom-conf").extend(sbt.Test)

  val projectDependencies = Seq(stubProject2 -> sbt.Compile)

  val unmanagedDependencies = Seq(
    existingFile("foo.jar") -> sbt.Compile,
    existingFile("bar.jar") -> sbt.Test
  )

  val moduleDependencies = Seq(
    ModuleIdentifier("com.example", "foo", "SNAPSHOT", Artifact.DefaultType, "") -> sbt.Compile,
    ModuleIdentifier("com.example", "bar", "SNAPSHOT", Artifact.DefaultType, "") -> sbt.Test
  )

  val unmanagedDependenciesWithCustomConf =
    unmanagedDependencies :+ (existingFile("baz.jar") -> CustomConf)

  val moduleDependenciesWithCustomConf =
    moduleDependencies :+ (ModuleIdentifier("com.example", "baz", "SNAPSHOT", Artifact.DefaultType, "") -> CustomConf)

  private def existingFile(path: String): File = new File(path) {
    override def isFile: Boolean = true
  }

  private def toBuildDependencies(deps: Seq[(ProjectRef, sbt.Configuration)]): BuildDependencies = {
    val asClasspthDep = deps.map { case (ref, conf) => ResolvedClasspathDependency(ref, Some(conf.name)) }
    BuildDependencies(Map(stubProject1 -> asClasspthDep), Map.empty)
  }

  private def toUnmanagedClasspath(deps: Seq[(File, sbt.Configuration)])(conf: sbt.Configuration): Keys.Classpath =
    deps.filter(_._2 == conf).map(_._1).map(Attributed(_)(AttributeMap.empty))

  private def toExternalDepenedncyClasspath(deps: Seq[(ModuleIdentifier, sbt.Configuration)])(conf: sbt.Configuration): Keys.Classpath = {
    val modules = deps.filter(_._2 == conf).map(_._1)
    val moduleIds = modules.map(id => ModuleID(id.organization, id.name, id.revision))
    val artifacts = modules.map(id => Artifact(id.name, id.classifier))
    moduleIds.zip(artifacts).map { case (id, artifact) =>
      Attributed(file("test.jar"))(AttributeMap.empty.put(sbt.Keys.moduleID.key, id).put(sbt.Keys.artifact.key, artifact))
    }
  }

  private def toProjectDependencyData(deps: Seq[(ProjectRef, sbt.Configuration)]): Seq[ProjectDependencyData] =
    deps.map { case (ref, conf) => ProjectDependencyData(ref.id, jb.Configuration.fromString(conf.name)) }

  private def toJarDependencyData(deps: Seq[(File, sbt.Configuration)]): Seq[JarDependencyData] =
    deps.map { case (file, conf) => JarDependencyData(file, fixTestConfigurations(jb.Configuration.fromString(conf.name))) }

  private def toModuleDependencyData(deps: Seq[(ModuleIdentifier, sbt.Configuration)]): Seq[ModuleDependencyData] =
    deps.map { case (id, conf) => ModuleDependencyData(id, fixTestConfigurations(jb.Configuration.fromString(conf.name))) }

  private def fixTestConfigurations(confs: Seq[jb.Configuration]): Seq[jb.Configuration] =
    confs.map(c => if (c.name == CustomConf.name) jb.Configuration.Test else c)
}