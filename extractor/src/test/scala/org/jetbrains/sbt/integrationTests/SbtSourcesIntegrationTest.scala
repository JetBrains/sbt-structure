package org.jetbrains.sbt.integrationTests

import java.io.File

import org.jetbrains.sbt.integrationTests.utils.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.*
import sbt.io.syntax.fileToRichFile

import scala.xml.XML

class SbtSourcesIntegrationTest extends AnyFreeSpec {
  private lazy val CurrentRepoSbtStructurePluginVersion: String =
    PluginArtifactsUtils.publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions

  "dumpSbtSourcesTo" - {
    "should run as a dynamically loaded plugin and report source archives for every loaded build" in {
      val sbtVersion = Version("1.12.12")
      val runOptions = CurrentEnvironment.buildSbtRunCommonOptions(sbtVersion)
      val projectDir = new File("extractor/src/test/data/1.3/sbt-sources-multiple").getCanonicalFile
      val responseFile = FileUtils.createTempFile("sbt-sources-response", ".xml")
      val pluginDefinition = FileUtils.createTempFile("sbt-sources-plugin", ".sbt")
      val sbtBinaryVersion = PluginArtifactsUtils.pluginSbtCrossVersionBinary(sbtVersion)

      FileUtils.writeStringToFile(
        pluginDefinition,
        s"""addSbtPlugin("org.jetbrains.scala" % "sbt-structure-extractor" % "$CurrentRepoSbtStructurePluginVersion", "$sbtBinaryVersion")"""
      )
      (runOptions.sbtGlobalBase / "plugins").mkdirs()

      val result = SbtProcessRunner.runSbtProcess(
        projectDir = projectDir,
        sbtCommands = Seq(s"""Global / dumpSbtSourcesTo "${responseFile.getCanonicalPath}""""),
        runOptions = runOptions.copy(launcherOptions = Seq(s"-addPluginSbtFile=${pluginDefinition.getCanonicalPath}"))
      )

      withClue(result.processOutput) {
        result.exitCode must equal(0)
      }
      responseFile must exist

      val xml = XML.loadFile(responseFile)
      xml.label must equal("sbtSources")
      (xml \@ "version") must equal("1")
      (xml \ "build").size must equal(2)
      (xml \\ "doc") mustBe empty
      (xml \\ "jar") mustBe empty
      val sourcesByBuild = (xml \ "build").map { build =>
        (build \ "uri").text -> (build \ "source").map(_.text)
      }.toMap
      sourcesByBuild.values.foreach { sources =>
        sources must not be empty
        sources.map(path => new File(path).getName).foreach(_ must endWith("-sources.jar"))
      }
      sourcesByBuild.values.reduce(_ intersect _) must not be empty
      result.processOutput must not include "The SBT source task must not generate managed sources"
    }
  }
}
