package org.jetbrains.sbt.integrationTests.utils

import org.jetbrains.sbt.integrationTests.utils.SbtProcessRunner.{ProcessRunResult, RunCommonOptions}

import java.io.File

object SbtStructureLoader {

  case class LoadResult(structure: String, processRunResult: ProcessRunResult)

  def dumpSbtStructure(
    project: File,
    sbtStructureOptions: String,
    pluginFile: File,
    runOptions: RunCommonOptions,
    expectedError: Option[String] = None,
    useDeprecatedDumpStructure: Boolean = false
  ): LoadResult = {
    val structureFile = FileUtils.createTempFile("sbt structure", ".xml")
    val sbtStructureOptionsPatched = s"download prettyPrint generateManagedSources $sbtStructureOptions"
    val sbtVersion = runOptions.sbtVersion
    val setOptionsCommand =
      s"""set ${scopedSbtSetting("""SettingKey[String]("sbtStructureOptions")""", "Global", sbtVersion)} := "$sbtStructureOptionsPatched""""
    val applyCreateTasksCommand =
      s"""apply -cp ${path(pluginFile)} org.jetbrains.sbt.CreateTasks"""
    val sbtCommands: Seq[String] =
      if (useDeprecatedDumpStructure) {
        Seq(
          s"""set ${scopedSbtSetting("""SettingKey[Option[File]]("sbtStructureOutputFile")""", "Global", sbtVersion)} := Some(file("${path(structureFile)}"))""",
          setOptionsCommand,
          applyCreateTasksCommand,
          s"""dumpStructure"""
        )
      } else {
        Seq(
          setOptionsCommand,
          applyCreateTasksCommand,
          s"""*/*:dumpStructureTo "${path(structureFile)}""""
        )
      }

    val processRunResult = SbtProcessRunner.runSbtProcess(
      project,
      sbtCommands,
      runOptions
    )

    expectedError match {
      case Some(errorString) =>
        assert(
          processRunResult.exitCode != 0,
          "Process was expected to fail but exited with code 0"
        )
        assert(
          processRunResult.processOutput.contains(errorString),
          s"Process output does not contain expected error string '$errorString'.\nProcess output:\n${processRunResult.processOutput}"
        )
        LoadResult("", processRunResult)
      case None =>
        assert(
          processRunResult.exitCode == 0,
          s"Process exited with non-zero code: ${processRunResult.exitCode}"
        )
        assert(
          structureFile.exists,
          "File must be created: " + structureFile.getPath
        )

        val structureString = FileUtils.read(structureFile)
        assert(
          structureString.nonEmpty,
          "structure dump was empty for project " + project.getPath
        )
        LoadResult(structureString, processRunResult)
    }
  }

  private def scopedSbtSetting(setting: String, scope: String, sbtVersion: Version): String = {
    val supportsSlashSyntax = sbtVersion >= Version("1.1")
    if (supportsSlashSyntax)
      s"""($scope / $setting)"""
    else
      s"""$setting in $scope"""
  }
}
