package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.*
import org.jetbrains.sbt.structure as jb
import org.scalatest.freespec.AnyFreeSpecLike
import org.jetbrains.sbt.structure.Configuration as jbConfig
import org.scalatest.matchers.must.Matchers.{contain, convertToAnyMustWrapper}
import sbt.{Attributed, globFilter as _, *}
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
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test, jbConfig.Runtime)
        ),
      ).extract

      val expected = DependencyData(
        projects = Dependencies(Nil, Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(jb.Configuration.Compile)
          )
        )),
        modules = Nil,
        jars = Seq(
          JarDependencyData(file("foo.jar"), Seq(jb.Configuration.Compile)),
          JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile)),
          JarDependencyData(file("baz.jar"), Seq(jb.Configuration.Test))
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
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test, jbConfig.Runtime)
        ),
      ).extract

      val expected = DependencyData(
        projects = Dependencies(Nil, Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(jb.Configuration.Compile)
          )
        )),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId("foo")),
            Seq(jb.Configuration.Compile)
          ),
          ModuleDependencyData(
            toIdentifier(moduleId("bar")),
            Seq(jb.Configuration.Compile)
          ),
          ModuleDependencyData(
            toIdentifier(moduleId("baz")),
            Seq(jb.Configuration.Test)
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
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test, jbConfig.Runtime)
        ),
      ).extract

      val expected = DependencyData(
        projects = Dependencies(Nil, Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(jb.Configuration.Compile)
          )
        )),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId("baz")),
            Seq(jb.Configuration.Test)
          ),
          ModuleDependencyData(
            toIdentifier(moduleId("qux")),
            Seq(jb.Configuration.Test)
          )
        ),
        jars = Seq(
          JarDependencyData(file("foo.jar"), Seq(jb.Configuration.Test)),
          JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Test))
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
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test, jbConfig.Runtime)
        ),
      ).extract

      val expectedModules = Seq(
        toIdentifier(moduleId),
        toIdentifier(moduleId).copy(classifier = "tests")
      )
      val expected = DependencyData(
        projects = Dependencies(Nil, Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(jb.Configuration.Compile)
          )
        )),
        modules = expectedModules.map(
          it => ModuleDependencyData(it, Seq(jb.Configuration.Compile))
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
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test, jbConfig.Runtime)
        ),
      ).extract

      val expected = DependencyData(
        projects = Dependencies(Nil, Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(jb.Configuration.Compile)
          )
        )),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId),
            Seq(jb.Configuration.Compile)
          )
        ),
        jars =
          Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile)))
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
        insertProjectTransitiveDependencies = true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test, jbConfig.Runtime)
        ),
      ).extract

      val expected = DependencyData(
        projects = Dependencies(Nil, Seq(
          ProjectDependencyData(
            projects(1).id,
            Some(projects(1).build),
            Seq(jb.Configuration.Compile)
          )
        )),
        modules = Seq(
          ModuleDependencyData(
            toIdentifier(moduleId),
            Seq(jb.Configuration.Provided)
          )
        ),
        jars = Seq(
          JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Provided))
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
        true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test, jbConfig.Runtime)
        ),
      ).extract

      val productionDependencies = Seq(
        ProjectDependencyData(projects(1).project, Some(projects(1).build), Seq(jb.Configuration.Compile))
      )
      val expected = DependencyData(
        projects = Dependencies(Nil, productionDependencies),
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
        true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jbConfig.Test)
        ),
      ).extract

      val productionDependencies = Seq(
        ProjectDependencyData(projects(1).project, Some(projects(1).build), Seq(jb.Configuration.Provided))
      )
      val expected = DependencyData(
        projects = Dependencies(Nil, productionDependencies),
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
        true,
        projectToConfigurations = Map(
          projects(1) -> Seq(jbConfig.Compile, jb.Configuration(CustomConf.name))
        ),
      ).extract

      val productionDependencies = Seq(
        ProjectDependencyData(projects(1).project, Some(projects(1).build), Seq(jb.Configuration.Provided))
      )
      val expected = DependencyData(
        projects = Dependencies(Nil, productionDependencies),
        modules = Nil,
        jars = Nil
      )
      assertIdentical(expected, actual)
    }

    "merge custom test configurations in non transitive dependencies" in {
      val actual = new DependenciesExtractor(
        projects.head,
        buildDependencies = apiAdapter.buildDependencies(
          Map(
            projects.head -> Seq(
              ResolvedClasspathDependency(projects(1), Some(CustomConf.name))
            ),
            projects(1) -> Seq.empty
          ),
        Map.empty),
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime, CustomConf),
        testConfigurations = Seq(sbt.Test, CustomConf),
        false,
        projectToConfigurations = Map.empty,
      ).extract

      val productionDependencies = Seq(
        ProjectDependencyData(projects(1).project, Some(projects(1).build), Seq(jb.Configuration.Test))
      )
      val expected = DependencyData(
        projects = Dependencies(Nil, productionDependencies),
        modules = Nil,
        jars = Nil
      )
      assertIdentical(expected, actual)
    }
  }


  def assertIdentical(expected: DependencyData, actual: DependencyData): Unit = {
    actual.projects.test must contain theSameElementsAs expected.projects.test
    actual.projects.production must contain theSameElementsAs expected.projects.production
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
