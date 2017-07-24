package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import org.specs2.mutable._
import sbt._


class RepositoryExtractorSpec extends Specification {

  "RepositoryExtractor" should {
    "extract modules for all accepted projects when supplied" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new RepositoryExtractor(
        projects = projects,
        updateReports = Map(
          projects(0) -> UpdateReportAdapter(Map(
            sbt.Compile.name -> Seq(
              ModuleReportAdapter(moduleId("foo"), Seq(Artifact("foo") -> file("foo.jar"))),
              ModuleReportAdapter(moduleId("bar"), Seq(Artifact("bar") -> file("bar.jar")))
            )
          )),
          projects(1) -> UpdateReportAdapter(Map(
            sbt.Compile.name -> Seq(
              ModuleReportAdapter(moduleId("foo"), Seq(Artifact("foo") -> file("foo.jar"))),
              ModuleReportAdapter(moduleId("baz"), Seq(Artifact("baz") -> file("baz.jar")))
            )
          ))
        ).apply,
        updateClassifiersReports = None,
        classpathTypes = const(Set(Artifact.DefaultType)),
        dependencyConfigurations = const(Seq(sbt.Compile))
      ).extract

      val expected = Seq(
        ModuleData(toIdentifier(moduleId("foo")), Set(file("foo.jar")), Set.empty, Set.empty),
        ModuleData(toIdentifier(moduleId("bar")), Set(file("bar.jar")), Set.empty, Set.empty),
        ModuleData(toIdentifier(moduleId("baz")), Set(file("baz.jar")), Set.empty, Set.empty)
      )
      actual.modules must containTheSameElementsAs(expected)
    }

    "extract modules with docs and sources for all accepted projects when supplied" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new RepositoryExtractor(
        projects = projects,
        updateReports = Map(
          projects(0) -> UpdateReportAdapter(Map(
            sbt.Compile.name -> Seq(
              ModuleReportAdapter(moduleId("foo"), Seq(Artifact("foo") -> file("foo.jar"))),
              ModuleReportAdapter(moduleId("bar"), Seq(Artifact("bar") -> file("bar.jar")))
            )
          )),
          projects(1) -> UpdateReportAdapter(Map(
            sbt.Compile.name -> Seq(
              ModuleReportAdapter(moduleId("foo"), Seq(Artifact("foo") -> file("foo.jar"))),
              ModuleReportAdapter(moduleId("baz"), Seq(Artifact("baz") -> file("baz.jar")))
            )
          ))
        ).apply,
        updateClassifiersReports = Some(Map(
          projects(0) -> UpdateReportAdapter(Map(
            sbt.Compile.name -> Seq(
              ModuleReportAdapter(moduleId("foo"), Seq(Artifact("foo", Artifact.DocType, "") -> file("foo-doc.jar"))),
              ModuleReportAdapter(moduleId("bar"), Seq(Artifact("bar", Artifact.DocType, "") -> file("bar-doc.jar")))
            )
          )),
          projects(1) -> UpdateReportAdapter(Map(
            sbt.Compile.name -> Seq(
              ModuleReportAdapter(moduleId("foo"), Seq(Artifact("foo", Artifact.SourceType, "") -> file("foo-src.jar"))),
              ModuleReportAdapter(moduleId("baz"), Seq(Artifact("baz", Artifact.SourceType, "") -> file("baz-src.jar")))
            )
          ))
        ).apply),
        classpathTypes = const(Set(Artifact.DefaultType)),
        dependencyConfigurations = const(Seq(sbt.Compile))
      ).extract

      val expected = Seq(
        ModuleData(toIdentifier(moduleId("foo")), Set(file("foo.jar")), Set(file("foo-doc.jar")), Set(file("foo-src.jar"))),
        ModuleData(toIdentifier(moduleId("bar")), Set(file("bar.jar")), Set(file("bar-doc.jar")), Set.empty),
        ModuleData(toIdentifier(moduleId("baz")), Set(file("baz.jar")), Set.empty, Set(file("baz-src.jar")))
      )
      actual.modules must containTheSameElementsAs(expected)
    }

    "extract module that have artifacts with classifiers as different modules" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new RepositoryExtractor(
        projects = projects.init,
        updateReports = Map(
          projects.head -> UpdateReportAdapter(Map(
            sbt.Compile.name -> Seq(
              ModuleReportAdapter(moduleId, Seq(
                Artifact("foo") -> file("foo.jar"),
                Artifact("foo", "tests") -> file("foo-tests.jar")))
            )
          ))
        ).apply,
        updateClassifiersReports = None,
        classpathTypes = const(Set(Artifact.DefaultType)),
        dependencyConfigurations = const(Seq(sbt.Compile))
      ).extract

      val expected = Seq(
        ModuleData(toIdentifier(moduleId), Set(file("foo.jar")), Set.empty, Set.empty),
        ModuleData(toIdentifier(moduleId).copy(classifier = "tests"), Set(file("foo-tests.jar")), Set.empty, Set.empty)
      )

      actual.modules must containTheSameElementsAs(expected)
    }
  }

  def toIdentifier(moduleId: ModuleID): ModuleIdentifier =
    ModuleIdentifier(moduleId.organization, moduleId.name, moduleId.revision, Artifact.DefaultType, "")

  val projects: Seq[ProjectRef] = Seq("project-1", "project-2").map(ProjectRef(file("/tmp/test-project"), _))
}
