package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt._
import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import org.specs2.matcher.Matcher
import org.specs2.mutable._
import sbt._

class DependenciesExtractorSpec extends Specification {

  "DependenciesExtractor" should {
    "always extract build dependencies" in {
      val actual = new DependenciesExtractor(projects(0),
        buildDependencies = Some(BuildDependencies(Map(
          projects(0) -> Seq(ResolvedClasspathDependency(projects(1), Some("compile")))
        ), Map.empty)),
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = emptyClasspath,
        dependencyConfigurations = Nil,
        testConfigurations = Nil
      ).extract

      val expected = DependencyData(
        Seq(ProjectDependencyData(projects(1).id, Seq(jb.Configuration.Compile))),
        Nil, Nil)
      actual must beIdenticalTo(expected)
    }

    "always extract unmanaged dependencies" in {
      val actual = new DependenciesExtractor(projects(0),
        buildDependencies = None,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(
            attributed(file("foo.jar")),
            attributed(file("bar.jar"))
          ),
          sbt.Test -> Seq(attributed(file("baz.jar")))
        ).apply,
        externalDependencyClasspath = emptyClasspath,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test)
      ).extract

      val expected = DependencyData(Nil, Nil, Seq(
        JarDependencyData(file("foo.jar"), Seq(jb.Configuration.Compile)),
        JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile)),
        JarDependencyData(file("baz.jar"), Seq(jb.Configuration.Test))
      ))
      actual must beIdenticalTo(expected)
    }

    "extract managed dependencies when supplied" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(projects(0),
        buildDependencies = None,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = Map(
          sbt.Compile -> Seq(
            attributedWith(file("foo.jar"))(moduleId("foo"), Artifact("foo")),
            attributedWith(file("bar.jar"))(moduleId("bar"), Artifact("bar"))
          ),
          sbt.Test -> Seq(
            attributedWith(file("baz.jar"))(moduleId("baz"), Artifact("baz"))
          )
        ).apply,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test)
      ).extract

      val expected = DependencyData(Nil, Seq(
        ModuleDependencyData(toIdentifier(moduleId("foo")), Seq(jb.Configuration.Compile)),
        ModuleDependencyData(toIdentifier(moduleId("bar")), Seq(jb.Configuration.Compile)),
        ModuleDependencyData(toIdentifier(moduleId("baz")), Seq(jb.Configuration.Test))
      ), Nil)

      actual must beIdenticalTo(expected)
    }

    "merge custom test configurations in unmanaged and managed dependencies" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(projects(0),
        buildDependencies = None,
        unmanagedClasspath = Map(
          sbt.Test    -> Seq(attributed(file("foo.jar"))),
          CustomConf  -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Map(
          sbt.Test    -> Seq(attributedWith(file("baz.jar"))(moduleId("baz"), Artifact("baz"))),
          CustomConf  -> Seq(attributedWith(file("qux.jar"))(moduleId("qux"), Artifact("qux")))
        ).apply,
        dependencyConfigurations = Seq(sbt.Test, CustomConf),
        testConfigurations = Seq(sbt.Test, CustomConf)
      ).extract

      val expected = DependencyData(Nil,
        modules = Seq(
          ModuleDependencyData(toIdentifier(moduleId("baz")), Seq(jb.Configuration.Test)),
          ModuleDependencyData(toIdentifier(moduleId("qux")), Seq(jb.Configuration.Test))
        ),
        jars = Seq(
          JarDependencyData(file("foo.jar"), Seq(jb.Configuration.Test)),
          JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Test))
        )
      )

      actual must beIdenticalTo(expected)
    }

    "extract managed dependency with classifier as different dependencies" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(projects(0),
        buildDependencies = None,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = Map(
          sbt.Compile -> Seq(
            attributedWith(file("foo.jar"))(moduleId, Artifact("foo")),
            attributedWith(file("foo-tests.jar"))(moduleId, Artifact("foo", "tests"))
          )
        ).apply,
        dependencyConfigurations = Seq(sbt.Compile),
        testConfigurations = Seq.empty
      ).extract

      val expectedModules = Seq(toIdentifier(moduleId), toIdentifier(moduleId).copy(classifier = "tests"))
      val expected = DependencyData(Nil, expectedModules.map(it => ModuleDependencyData(it, Seq(jb.Configuration.Compile))), Nil)
      actual must beIdenticalTo(expected)
    }

    "merge (compile, test, runtime) -> compile in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(projects(0),
        buildDependencies = None,
        externalDependencyClasspath = Map(
          sbt.Compile -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))),
          sbt.Test    -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))),
          sbt.Runtime -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo")))
        ).apply,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          sbt.Test    -> Seq(attributed(file("bar.jar"))),
          sbt.Runtime -> Seq(attributed(file("bar.jar")))
        ).apply,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Seq.empty
      ).extract

      val expected = DependencyData(Nil,
        Seq(ModuleDependencyData(toIdentifier(moduleId), Seq(jb.Configuration.Compile))),
        Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile)))
      )
      actual must beIdenticalTo(expected)
    }

    "merge (compile, test) -> provided in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(projects(0),
        buildDependencies = None,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          sbt.Test    -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Map(
          sbt.Compile -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))),
          sbt.Test    -> Seq(attributedWith(file("foo.jar"))(moduleId, Artifact("foo")))
        ).apply,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq.empty
      ).extract

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

  def attributed(file: File): Attributed[File] =
    Attributed(file)(AttributeMap.empty)

  def toIdentifier(moduleId: ModuleID): ModuleIdentifier =
    ModuleIdentifier(moduleId.organization, moduleId.name, moduleId.revision, Artifact.DefaultType, "")

  val projects = Seq("project-1", "project-2").map(ProjectRef(file("/tmp/test-project"), _))
  val emptyClasspath: sbt.Configuration => Keys.Classpath = _ => Nil
  val CustomConf = config("custom-conf").extend(sbt.Test)
}