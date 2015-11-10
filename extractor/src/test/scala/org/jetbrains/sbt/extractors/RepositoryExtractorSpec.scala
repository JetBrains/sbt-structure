package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{structure => jb}
import org.specs2.mutable._
import sbt._


class RepositoryExtractorSpec extends Specification {

  "RepositoryExtractor" should {
    "extract modules for all accepted projects when supplied" in {
      val actual = new RepositoryExtractor(projects, updateReports.apply, None,
        const(Set(Artifact.DefaultType)), const(Seq(sbt.Compile, sbt.Test))).extract
      val expected = toModuleData(modules).map(_.copy(docs = Set.empty, sources = Set.empty))
      actual.modules must containTheSameElementsAs(expected)
    }

    "extract modules with docs and sources for all accepted projects when supplied" in {
      val actual = new RepositoryExtractor(projects, updateReports.apply, Some(updateClassifiersReports.apply),
        const(Set(Artifact.DefaultType)), const(Seq(sbt.Compile, sbt.Test))).extract
      val expected = toModuleData(modules)
      actual.modules must containTheSameElementsAs(expected)
    }

    "extract module that have artifacts with classifiers as different modules" in {
      val updateReport = Map(projects(0) -> UpdateReportAdapter(Map(
        sbt.Compile.name -> Seq(ModuleReportAdapter(
          ModuleID("com.example", "foo", "SNAPSHOT"), Seq(
            Artifact("foo") -> file("foo.jar"),
            Artifact("foo", "tests") -> file("foo-tests.jar")
        )))
      )))

      val actual = new RepositoryExtractor(projects.init, updateReport.apply, None,
        const(Set(Artifact.DefaultType)), const(Seq(sbt.Compile))).extract
      val expected = Seq(
        ModuleData(ModuleIdentifier("com.example", "foo", "SNAPSHOT", Artifact.DefaultType, ""), Set(file("foo.jar")), Set.empty, Set.empty),
        ModuleData(ModuleIdentifier("com.example", "foo", "SNAPSHOT", Artifact.DefaultType, "tests"), Set(file("foo-tests.jar")), Set.empty, Set.empty)
      )

      actual.modules must containTheSameElementsAs(expected)
    }
  }

  val projects = {
    val base = file("/tmp/test-project")
    Seq("project-1", "project-2").map(ProjectRef(base, _))
  }

  val modules = Seq(
    ModuleIdentifier("com.example", "foo", "SNAPSHOT", Artifact.DefaultType, "") ->
      (Set(file("foo.jar")), Set(file("foo-doc.jar")), Set(file("foo-src.jar"))),
    ModuleIdentifier("com.example", "bar", "SNAPSHOT", Artifact.DefaultType, "") ->
      (Set(file("bar.jar")), Set(file("bar-doc.jar")), Set(file("bar-src.jar")))
  )

  private def toModuleReportWithoutDocs(modules: Seq[(ModuleIdentifier, (Set[File], Set[File], Set[File]))]): Seq[ModuleReportAdapter] =
    modules.map { case (id, (bin, _, _)) =>
      ModuleReportAdapter(
        ModuleID(id.organization, id.name, id.revision),
        bin.toSeq.map(f => Artifact(id.name, id.artifactType, "", id.classifier) -> f)
      )
    }

  private def toModuleReportDocsOnly(modules: Seq[(ModuleIdentifier, (Set[File], Set[File], Set[File]))]): Seq[ModuleReportAdapter] =
    modules.map { case (id, (_, doc, src)) =>
      def toArtifactFile(`type`: String)(file: File): (Artifact, File) =
        Artifact(id.name, `type`, "", id.classifier) -> file

      ModuleReportAdapter(
        ModuleID(id.organization, id.name, id.revision),
        doc.toSeq.map(toArtifactFile(Artifact.DocType)) ++ src.toSeq.map(toArtifactFile(Artifact.SourceType))
      )
    }

  private def toModuleData(modules: Seq[(ModuleIdentifier, (Set[File], Set[File], Set[File]))]): Seq[ModuleData] =
    modules.map { case (id, (bin, doc, src)) => ModuleData(id, bin, doc, src) }

  val updateReport = Map(
    sbt.Compile.name -> toModuleReportWithoutDocs(modules),
    sbt.Test.name    -> toModuleReportWithoutDocs(modules)
  )

  val updateClassifiersReport = Map(
    sbt.Compile.name -> toModuleReportDocsOnly(modules),
    sbt.Test.name    -> toModuleReportDocsOnly(modules)
  )

  val updateReports = Map(
    projects(0) -> UpdateReportAdapter(updateReport),
    projects(1) -> UpdateReportAdapter(updateReport)
  )

  val updateClassifiersReports = Map(
    projects(0) -> UpdateReportAdapter(updateClassifiersReport),
    projects(1) -> UpdateReportAdapter(updateClassifiersReport)
  )
}
