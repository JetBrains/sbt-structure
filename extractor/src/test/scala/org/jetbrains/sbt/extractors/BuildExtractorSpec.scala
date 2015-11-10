package org.jetbrains.sbt
package extractors

import org.specs2.mutable._
import org.jetbrains.sbt.structure._
import sbt._

class BuildExtractorSpec extends Specification {

  "BuildExtractor" should {
    "always extract imports and plugins' files" in {
      val actual = new BuildExtractor(stubLoadedBuildUnitAdapter, None).extract
      actual must beEqualTo(BuildData(stubImports, stubPlugins, Nil, Nil))
    }

    "extract plugins sources and docs when supplied" in {
      val actual = new BuildExtractor(stubLoadedBuildUnitAdapter, Some(stubUpdateClassifiersReport)).extract
      actual must beEqualTo(BuildData(stubImports, stubPlugins, stubDocs, stubSources))
    }
  }

  val stubImports = Seq("import foo.bar", "import bar.baz")
  val stubPlugins = Seq("foo.jar").map(file)

  val stubLoadedBuildUnitAdapter = new LoadedBuildUnitAdapter(null) {
    override def imports = stubImports
    override def pluginsClasspath =
      stubPlugins.map(Attributed(_)(AttributeMap.empty))
  }

  val stubDocs = Seq("foo-docs.jar").map(file)
  val stubSources = Seq("bar-sources.jar").map(file)

  def toModuleReportAdapter(artifactType: String)(file: File): ModuleReportAdapter = {
    val moduleId = ModuleID("example.com", file.getName, "SNAPSHOT")
    val artifact = Artifact(file.getName, artifactType, Artifact.DefaultExtension)
    ModuleReportAdapter(moduleId, Seq(artifact -> file))
  }

  val stubUpdateClassifiersReport = UpdateReportAdapter(Map(
    Compile.name ->(
      stubDocs.map(toModuleReportAdapter(Artifact.DocType)) ++
      stubSources.map(toModuleReportAdapter(Artifact.SourceType))
    )))
}
