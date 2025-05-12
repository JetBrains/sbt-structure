package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.ProjectRefOps
import org.jetbrains.sbt.extractors.DependenciesExtractor.ProductionType
import org.jetbrains.sbt.structure.*
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.{contain, convertToAnyMustWrapper}
import sbt.{Configuration => SbtConfiguration, Attributed, globFilter as _, *}

import scala.collection.Seq

class DependenciesExtractorSpec_ProdTestSourcesSeparatedEnabled extends AnyFreeSpecLike {

  val projects: Seq[ProjectRef] = Seq("project-1", "project-2", "project-3").map { projectName =>
    ProjectRef(file("/tmp/test-project"), projectName)
  }
  val emptyClasspath: sbt.Configuration => Keys.Classpath = _ => Nil
  val CustomConf = config("custom-conf").extend(sbt.Test)

  "DependenciesExtractor for managed and unmanaged dependencies" - {

    "always extract unmanaged dependencies" in {
      val actual = new DependenciesExtractor(
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(
            attributed(file("foo.jar")),
            attributed(file("bar.jar"))
          ),
          sbt.Test -> Seq(attributed(file("foo.jar")))
        ).apply,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        separateProdTestSources = true,
        projectToConfigurations = Map(
          ProductionType(projects(1)) -> Seq(Configuration.Test),
          ProductionType(projects.head) -> Seq(Configuration.Test),
        )
      ).extract

      val expected = DependencyData(
        projects = Dependencies(
          forProduction = Seq.empty,
          forTest = Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            ),
            ProjectDependencyData(
              s"${projects.head.id}:main",
              Some(projects.head.build),
              Seq(Configuration.Compile)
            )
          ),
        ),
        modules = Dependencies(Seq.empty, Seq.empty),
        jars = Dependencies(
          forProduction = Seq(
            JarDependencyData(file("foo.jar"), Seq(Configuration.Provided)),
            JarDependencyData(file("bar.jar"), Seq(Configuration.Provided)),
          ),
          forTest = Seq(
            JarDependencyData(file("foo.jar"), Seq(Configuration.Compile))
          )
        )
      )
      assertIdentical(expected, actual)
    }

    "extract managed dependencies when supplied" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(
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
        separateProdTestSources = true,
        projectToConfigurations = Map(
          ProductionType(projects(1)) -> Seq(Configuration.Test),
          ProductionType(projects.head) -> Seq(Configuration.Test)
        )
      ).extract

      val expected = DependencyData(
        projects = Dependencies(
          forProduction = Seq.empty,
          forTest = Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            ),
            ProjectDependencyData(
              s"${projects.head.id}:main",
              Some(projects.head.build),
              Seq(Configuration.Compile)
            )
          ),
        ),
        modules = Dependencies(
          forProduction = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId("foo")),
              Seq(Configuration.Provided)
            ),
            ModuleDependencyData(
              toIdentifier(moduleId("bar")),
              Seq(Configuration.Provided)
            )
          ),
          forTest = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId("baz")),
              Seq(Configuration.Compile)
            )
          )
        ),
        jars = Dependencies(Seq.empty, Seq.empty)
      )

      assertIdentical(expected, actual)
    }

    "merge custom test configurations in unmanaged and managed dependencies" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(
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
        separateProdTestSources = true,
        projectToConfigurations = Map(
          ProductionType(projects(1)) -> Seq(Configuration.Test),
          ProductionType(projects.head) -> Seq(Configuration.Test)
        )
      ).extract

      val expected = DependencyData(
        projects = Dependencies(
          forProduction = Seq.empty,
          forTest = Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            ),
            ProjectDependencyData(
              s"${projects.head.id}:main",
              Some(projects.head.build),
              Seq(Configuration.Compile)
            )
          ),
        ),
        modules = Dependencies(
          forProduction = Seq.empty,
          forTest = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId("baz")),
              Seq(Configuration.Compile)
            ),
            ModuleDependencyData(
              toIdentifier(moduleId("qux")),
              Seq(Configuration.Compile)
            )
          )
        ),
        jars = Dependencies(
          forProduction = Seq.empty,
          forTest = Seq(
            JarDependencyData(file("foo.jar"), Seq(Configuration.Compile)),
            JarDependencyData(file("bar.jar"), Seq(Configuration.Compile))
          )
        )
      )

      assertIdentical(expected, actual)
    }

    "extract managed dependency with classifier as different dependencies" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = Some(Map(
          sbt.Compile -> Seq(
            attributedWith(file("foo.jar"))(moduleId, Artifact("foo")),
            attributedWith(file("foo-tests.jar"))(
              moduleId,
              Artifact("foo", "tests")
            )
          ),
          sbt.Test -> Seq.empty
        )),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        separateProdTestSources = true,
        projectToConfigurations = Map(
          ProductionType(projects(1)) -> Seq(Configuration.Test),
          ProductionType(projects.head) -> Seq(Configuration.Test)

        )
      ).extract

      val expectedModules = Seq(toIdentifier(moduleId), toIdentifier(moduleId).copy(classifier = "tests")).map {
        it => ModuleDependencyData(it, Seq(Configuration.Provided))
      }
      val expected = DependencyData(
        projects = Dependencies(
          forProduction = Seq.empty,
          forTest = Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            ),
            ProjectDependencyData(
              s"${projects.head.id}:main",
              Some(projects.head.build),
              Seq(Configuration.Compile)
            )
          ),
        ),
        modules = Dependencies(
          forProduction = expectedModules, forTest= Seq.empty
        ),
        jars = Dependencies(Seq.empty, Seq.empty)
      )
      assertIdentical(expected, actual)
    }
  }

  "Validation of mapping configurations to prod and test modules in unmanaged deps, managed deps an project deps" - {

    "Convert (compile, test, runtime) -> to prod compile and test compile" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
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
        testConfigurations = Seq(sbt.Test),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        separateProdTestSources = true,
        projectToConfigurations = Map(
          ProductionType(projects(1)) -> Seq(Configuration.Compile, Configuration.Test, Configuration.Runtime),
          ProductionType(projects.head) -> Seq(Configuration.Test)
        )
      ).extract

      val expected = DependencyData(
        projects = Dependencies(
          forProduction = Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            )
          ),
          forTest = Seq(
            ProjectDependencyData(
              s"${projects.head.id}:main",
              Some(projects.head.build),
              Seq(Configuration.Compile)
            ),
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            )
          )
        ),
        modules = Dependencies(
          forProduction = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId),
              Seq(Configuration.Compile)
            )
          ),
          forTest = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId),
              Seq(Configuration.Compile)
            )
          )
        ),
        jars = Dependencies(
          forProduction = Seq(
            JarDependencyData(
              file("bar.jar"),
              Seq(Configuration.Compile)
            )
          ),
          forTest = Seq(
            JarDependencyData(
              file("bar.jar"),
              Seq(Configuration.Compile)
            )
          )
        ),
      )
      assertIdentical(expected, actual)
    }

    "Convert (compile, test) -> to prod provided and test compile" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          sbt.Test -> Seq(attributed(file("bar.jar"))),
          sbt.Runtime -> Seq.empty
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Test -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Runtime -> Seq.empty
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Seq(sbt.Test),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        separateProdTestSources = true,
        projectToConfigurations = Map(
          ProductionType(projects(1)) -> Seq(Configuration.Test, Configuration.Compile),
          ProductionType(projects.head) -> Seq(Configuration.Test)
        )
      ).extract

      val expected = DependencyData(
        projects = Dependencies(
          forProduction =  Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Provided)
            )
          ),
          forTest = Seq(
            ProjectDependencyData(
              s"${projects.head.id}:main",
              Some(projects.head.build),
              Seq(Configuration.Compile)
            ),
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            )
          )
        ),
        modules = Dependencies(
          forProduction = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId),
              Seq(Configuration.Provided)
            )
          ),
          forTest = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId),
              Seq(Configuration.Compile)
            )
          )
        ),
        jars = Dependencies(
          forProduction = Seq(
            JarDependencyData(
              file("bar.jar"),
              Seq(Configuration.Provided)
            )
          ),
          forTest = Seq(
            JarDependencyData(
              file("bar.jar"),
              Seq(Configuration.Compile)
            )
          )
        )
      )
      assertIdentical(expected, actual)
    }

    "convert (custom test configurations, compile) -> prod provided and test compile" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual =  new DependenciesExtractor(
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          CustomConf -> Seq(attributed(file("bar.jar"))),
          sbt.Runtime -> Seq.empty,
          sbt.Test -> Seq.empty,
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            CustomConf -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Runtime -> Seq.empty,
            sbt.Test -> Seq.empty,
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime, CustomConf),
        testConfigurations = Seq(sbt.Test, CustomConf),
        sourceConfigurations = Seq(sbt.Compile, sbt.Runtime),
        separateProdTestSources = true,
        projectToConfigurations = Map(
          ProductionType(projects(1)) -> Seq(Configuration.Compile, Configuration(CustomConf.name)),
          ProductionType(projects.head) -> Seq(Configuration.Test)
        )
      ).extract

      val expected = DependencyData(
        projects = Dependencies(
          forProduction = Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Provided)
            )
          ),
          forTest = Seq(
            ProjectDependencyData(
              s"${projects(1).id}:main",
              Some(projects(1).build),
              Seq(Configuration.Compile)
            ),
            ProjectDependencyData(
              s"${projects.head.id}:main",
              Some(projects.head.build),
              Seq(Configuration.Compile)
            ),
          )
        ),
        modules = Dependencies(
          forProduction = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId),
              Seq(Configuration.Provided)
            )
          ),
          forTest = Seq(
            ModuleDependencyData(
              toIdentifier(moduleId),
              Seq(Configuration.Compile)
            )
          )
        ),
        jars = Dependencies(
          forProduction = Seq(
            JarDependencyData(
              file("bar.jar"),
              Seq(Configuration.Provided)
            )
          ),
          forTest = Seq(
            JarDependencyData(
              file("bar.jar"),
              Seq(Configuration.Compile)
            )
          )
        )
      )
      assertIdentical(expected, actual)
    }
  }

  def assertIdentical(expected: DependencyData, actual: DependencyData): Unit = {
    actual.projects.forProduction must contain theSameElementsAs expected.projects.forProduction
    actual.projects.forTest must contain theSameElementsAs expected.projects.forTest
    actual.jars.forProduction must contain theSameElementsAs expected.jars.forProduction
    actual.jars.forTest must contain theSameElementsAs expected.jars.forTest
    actual.modules.forProduction must contain theSameElementsAs expected.modules.forProduction
    actual.modules.forTest must contain theSameElementsAs expected.modules.forTest
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
