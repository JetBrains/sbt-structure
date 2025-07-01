package org.jetbrains.sbt.integrationTests

import org.jetbrains.sbt.integrationTests.utils.LatestSbtVersions.*
import org.jetbrains.sbt.integrationTests.utils.SbtProcessRunner.ProcessRunResult
import org.jetbrains.sbt.integrationTests.utils.{CurrentEnvironment, FileUtils, PluginArtifactsUtils, SbtProcessRunner, SbtProjectFilesUtils, Version}
import org.scalatest.AppendedClues.convertToClueful
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RunSbtWithPluginIntegrationTest extends AnyFreeSpecLike {
  private lazy val CurrentRepoSbtIdeaPluginVersion: String =
    PluginArtifactsUtils.publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions

  "sbt-extractor plugin should not warn about unused settings when sbt is launched" - {
    // Note: I didn't add tests before sbt 1.4 because it's not compatible with most up-to-date JDK versions,
    // and making the tests work requires more fine-tuning for the JDK in tests.
    // sbt 1.3 is quite old, so it's fine not to test it for this particular non-major test (no redundant warnings)
    "1.4" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_4)
    "1.5" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_5)
    "1.6" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_6)
    "1.7" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_7)
    "1.8" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_8)
    "1.9" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_9)
    "1.10" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_10)
    "1.11" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_1_11)
    "2.0" in runBlankSbtProcessWithPluginOutputTest(SbtVersion_2)
  }

  private def runBlankSbtProcessWithPluginOutputTest(sbtVersion: Version): Unit = {
    val runResult = runBlankSbtProcessWithPluginAndShutdown(sbtVersion)
    assertNoUnexpectedWarnings(runResult.processOutput)
  }

  private def runBlankSbtProcessWithPluginAndShutdown(sbtVersion: Version): ProcessRunResult = {
    val tempProjectDir = FileUtils.createTempDirectory(s"sbt-project-$sbtVersion")
    SbtProjectFilesUtils.updateSbtStructurePluginToVersion(tempProjectDir, CurrentRepoSbtIdeaPluginVersion, sbtVersion)

    val runOptions = CurrentEnvironment.buildSbtRunCommonOptions(sbtVersion)
    val runResult = SbtProcessRunner.runSbtProcess(
      projectDir = tempProjectDir,
      sbtCommands = Seq("shutdown"),
      runOptions = runOptions
    )
    runResult.exitCode shouldBe 0 withClue "Process terminated with non-zero exit code"
    runResult
  }

  private val ForbiddenOutputTexts = Seq(
    "[warn] there's a key that's not used by any other settings/tasks",
    "sbt 0.13 shell syntax is deprecated; use slash syntax instead"
  )

  private def assertNoUnexpectedWarnings(processOutput: String): Unit = {
    ForbiddenOutputTexts.foreach { text =>
      if (processOutput.contains(text)) {
        fail(s"Process output must not contain output: $text")
      }
    }
  }
}
