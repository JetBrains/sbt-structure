package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.{contain, convertToAnyMustWrapper}
import sbt.{Configuration => SbtConfiguration, Attributed, globFilter => _, _}
import sbt.jetbrains.apiAdapter

import scala.collection.Seq
class DependenciesExtractorSpec extends AnyFreeSpecLike {

  val projects: Seq[ProjectRef] =
    Seq("project-1", "project-2").map(ProjectRef(file("/tmp/test-project"), _))
  val emptyClasspath: sbt.Configuration => Keys.Classpath = _ => Nil
  val CustomConf = config("custom-conf").extend(sbt.Test)
  val buildDependencies = apiAdapter.buildDependencies(
    Map(
      projects.head -> Seq(
        ResolvedClasspathDependency(projects(1), Some("compile"))
      ),
      projects(1) -> Seq.empty
    ),
    Map.empty
  )

  "DependenciesExtractor for managed and unmanaged dependencies" - {

    "always extract unmanaged dependencies" in {
      val actual = new DependenciesExtractor(
        projects.head,
        buildDependencies = buildDependencies,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(
            attributed(file("foo.jar")),
            attributed(file("bar.jar"))
          ),
          sbt.Test -> Seq(attributed(file("baz.jar")))
        ).apply,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Runtime, Configuration.Test)
        )
      ).extract

      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(Configuration.Compile)
          )
        ),
        modules = Nil,
        jars = Seq(
          JarDependencyData(file("foo.jar"), Seq(Configuration.Compile)),
          JarDependencyData(file("bar.jar"), Seq(Configuration.Compile)),
          JarDependencyData(file("baz.jar"), Seq(Configuration.Test))
        )
      )
      assertIdentical(expected, actual)
    }

    "extract managed dependencies when supplied" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(
        projects.head,
        buildDependencies = buildDependencies,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId("foo"), Artifact("foo")),
              attributedWith(file("bar.jar"))(moduleId("bar"), Artifact("bar"))
            ),
            sbt.Test -> Seq(
              attributedWith(file("baz.jar"))(moduleId("baz"), Artifact("baz"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime)
        )
      ).extract

      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(Configuration.Compile)
          )
        ),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId("foo")),
            Seq(Configuration.Compile)
          ),
          ModuleDependencyData(
            toIdentifier(moduleId("bar")),
            Seq(Configuration.Compile)
          ),
          ModuleDependencyData(
            toIdentifier(moduleId("baz")),
            Seq(Configuration.Test)
          )
        ),
        jars = Nil
      )

      assertIdentical(expected, actual)
    }

    "merge custom test configurations in unmanaged and managed dependencies" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(
        projects.head,
        buildDependencies = buildDependencies,
        unmanagedClasspath = Map(
          sbt.Test -> Seq(attributed(file("foo.jar"))),
          CustomConf -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Test -> Seq(
              attributedWith(file("baz.jar"))(moduleId("baz"), Artifact("baz"))
            ),
            CustomConf -> Seq(
              attributedWith(file("qux.jar"))(moduleId("qux"), Artifact("qux"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Test, CustomConf),
        testConfigurations = Seq(sbt.Test, CustomConf),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime)
        )
      ).extract

      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(Configuration.Compile)
          )
        ),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId("baz")),
            Seq(Configuration.Test)
          ),
          ModuleDependencyData(
            toIdentifier(moduleId("qux")),
            Seq(Configuration.Test)
          )
        ),
        jars = Seq(
          JarDependencyData(file("foo.jar"), Seq(Configuration.Test)),
          JarDependencyData(file("bar.jar"), Seq(Configuration.Test))
        )
      )

      assertIdentical(expected, actual)
    }

    "extract managed dependency with classifier as different dependencies" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        projects.head,
        buildDependencies = buildDependencies,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo")),
              attributedWith(file("foo-tests.jar"))(
                moduleId,
                Artifact("foo", "tests")
              )
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile),
        testConfigurations = Seq.empty,
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime)
        )
      ).extract

      val expectedModules = Seq(
        toIdentifier(moduleId),
        toIdentifier(moduleId).copy(classifier = "tests")
      )
      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(Configuration.Compile)
          )
        ),
        modules = expectedModules.map(
          it => ModuleDependencyData(it, Seq(Configuration.Compile))
        ),
        jars = Nil
      )
      assertIdentical(expected, actual)
    }

    "merge (compile, test, runtime) -> compile in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        projects.head,
        buildDependencies = buildDependencies,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          sbt.Test -> Seq(attributed(file("bar.jar"))),
          sbt.Runtime -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Test -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Runtime -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Seq.empty,
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime)
        )
      ).extract

      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(Configuration.Compile)
          )
        ),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId),
            Seq(Configuration.Compile)
          )
        ),
        jars =
          Seq(JarDependencyData(file("bar.jar"), Seq(Configuration.Compile)))
      )
      assertIdentical(expected, actual)
    }

    "merge (compile, test) -> provided in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        projects.head,
        buildDependencies = buildDependencies,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          sbt.Test -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Test -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq.empty,
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime)
        )
      ).extract

      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(Configuration.Compile)
          )
        ),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId),
            Seq(Configuration.Provided)
          )
        ),
        jars = Seq(
          JarDependencyData(file("bar.jar"), Seq(Configuration.Provided))
        )
      )
      assertIdentical(expected, actual)
    }
  }

  "DependenciesExtractor for project dependencies" - {

    "merge (compile, test, runtime) -> compile in transitive project dependencies to match IDEA scopes" in {
      val actual =  new DependenciesExtractor(
        projects.head,
        buildDependencies,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Nil,
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime)
        )
      ).extract

      val productionDependencies = Seq(
        ProjectDependencyData(projects(1).project, Some(projects(1).build), Seq(Configuration.Compile))
      )
      val expected = DependencyData(
        projects = productionDependencies,
        modules = Nil,
        jars = Nil
      )
      assertIdentical(expected, actual)
    }

    "merge (compile, test) -> provided in transitive project dependencies to match IDEA scopes" in {
      val actual =  new DependenciesExtractor(
        projects.head,
        buildDependencies,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Nil,
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration.Test)
        )
      ).extract

      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(projects(1).project, Some(projects(1).build), Seq(Configuration.Provided))
        ),
        modules = Nil,
        jars = Nil
      )
      assertIdentical(expected, actual)
    }

    "merge (custom test configurations, compile) -> provided in transitive project dependencies to match IDEA scopes" in {
      val actual =  new DependenciesExtractor(
        projects.head,
        buildDependencies,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime, CustomConf),
        testConfigurations = Seq(sbt.Test, CustomConf),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        true,
        projectToConfigurations = Map(
          projects(1) -> Seq(Configuration.Compile, Configuration(CustomConf.name))
        )
      ).extract

      val expected = DependencyData(
        projects = Seq(
          ProjectDependencyData(projects(1).project, Some(projects(1).build), Seq(Configuration.Provided))
        ),
        modules = Nil,
        jars = Nil
      )
      assertIdentical(expected, actual)
    }

  }


  def assertIdentical(expected: DependencyData, actual: DependencyData): Unit = {
    actual.projects must contain theSameElementsAs expected.projects
    actual.jars must contain theSameElementsAs expected.jars
    actual.modules must contain theSameElementsAs expected.modules
  }

  def attributedWith(file: File)(moduleId: ModuleID,
                                 artifact: Artifact): Attributed[File] =
    Attributed(file)(
      AttributeMap.empty
        .put(sbt.Keys.moduleID.key, moduleId)
        .put(sbt.Keys.artifact.key, artifact)
    )

  def attributed(file: File): Attributed[File] =
    Attributed(file)(AttributeMap.empty)

  def toIdentifier(moduleId: ModuleID): ModuleIdentifier =
    ModuleIdentifier(
      moduleId.organization,
      moduleId.name,
      moduleId.revision,
      Artifact.DefaultType,
      ""
    )
}
