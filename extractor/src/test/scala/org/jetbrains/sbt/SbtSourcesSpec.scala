package org.jetbrains.sbt

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.jetbrains.sbt.sources.AtomicFileWriter
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.{convertToAnyMustWrapper, equal}
import sbt.{Artifact, Compile, ModuleID, ProjectRef}

import scala.xml.XML

class SbtSourcesSpec extends AnyFreeSpec {
  "SbtSourcesReport" - {
    "should retain only source artifacts and preserve their build ownership" in {
      val firstBuild = ProjectRef(new URI("file:/first-build"), "root")
      val secondBuild = ProjectRef(new URI("file:/second-build"), "root")
      val sharedSource = new File("shared-sources.jar")

      val report = SbtSourcesReport.fromUpdateReports(Seq(
        firstBuild -> updateReport(
          Artifact("classes", Artifact.DefaultType, Artifact.DefaultExtension) -> new File("classes.jar"),
          Artifact("docs", Artifact.DocType, Artifact.DefaultExtension) -> new File("docs.jar"),
          Artifact("shared", Artifact.SourceType, Artifact.DefaultExtension) -> sharedSource
        ),
        secondBuild -> updateReport(
          Artifact("shared", Artifact.SourceType, Artifact.DefaultExtension) -> sharedSource,
          Artifact("other", Artifact.SourceType, Artifact.DefaultExtension) -> new File("other-sources.jar")
        )
      ))

      report.builds.map(_.uri) must equal(Seq(firstBuild.build, secondBuild.build))
      report.builds.head.sources must equal(Seq(sharedSource.getCanonicalFile))
      report.builds(1).sources must equal(Seq(new File("other-sources.jar").getCanonicalFile, sharedSource.getCanonicalFile).sortBy(_.getPath))
    }

    "should serialize normalized URIs and deterministic source-only XML" in {
      val firstSource = new File("z-sources.jar")
      val secondSource = new File("a-sources.jar")
      val report = SbtSourcesReport(Seq(
        SbtBuildSources(new URI("file:/workspace/alpha/../beta"), Seq(firstSource, secondSource, firstSource)),
        SbtBuildSources(new URI("file:/workspace/empty"), Seq.empty)
      ))

      val xml = XML.loadString(SbtSourcesXml.render(report))

      xml.label must equal("sbtSources")
      (xml \@ "version") must equal("1")
      (xml \ "build").map(node => (node \ "uri").text) must equal(Seq("file:/workspace/beta", "file:/workspace/empty"))
      ((xml \ "build").head \ "source").map(_.text) must equal(Seq(secondSource.getCanonicalPath, firstSource.getCanonicalPath))
      (xml \\ "doc").isEmpty must equal(true)
      (xml \\ "jar").isEmpty must equal(true)
    }
  }

  "AtomicFileWriter" - {
    "should replace an existing response file" in {
      val output = Files.createTempFile("sbt-sources-response", ".xml").toFile
      Files.write(output.toPath, "old response".getBytes(StandardCharsets.UTF_8))

      AtomicFileWriter.writeUtf8(output, "new response")

      new String(Files.readAllBytes(output.toPath), StandardCharsets.UTF_8) must equal("new response")
    }
  }

  private def updateReport(artifacts: (Artifact, File)*): UpdateReportAdapter =
    UpdateReportAdapter(Map(
      Compile.name -> Seq(ModuleReportAdapter(ModuleID("example", "module", "1.0"), artifacts))
    ))
}
