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
  ): LoadResult = {
    val structureFile = FileUtils.createTempFile("sbt-structure", ".xml")
    val sbtStructureOptionsPatched = s"download prettyPrint $sbtStructureOptions"
    val sbtVersion = runOptions.sbtVersion
    val sbtCommands: Seq[String] = Seq(
      s"""set ${scopedSbtSetting("""SettingKey[Option[File]]("sbtStructureOutputFile")""", "Global", sbtVersion)} := Some(file("${path(structureFile)}"))""",
      s"""set ${scopedSbtSetting("""SettingKey[String]("sbtStructureOptions")""", "Global", sbtVersion)} := "$sbtStructureOptionsPatched"""",
      s"""set ${scopedSbtSetting("""SettingKey[Boolean]("generateManagedSourcesDuringStructureDump")""", "Global", sbtVersion)} := true""",
      s"""apply -cp ${path(pluginFile)} org.jetbrains.sbt.CreateTasks""",
      s"""dumpStructure"""
    )

    val processRunResult = SbtProcessRunner.runSbtProcess(
      project,
      sbtCommands,
      runOptions
    )

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

  private def scopedSbtSetting(setting: String, scope: String, sbtVersion: Version): String = {
    val supportsSlashSyntax = sbtVersion >= Version("1.1")
    if (supportsSlashSyntax)
      s"""($scope / $setting)"""
    else
      s"""$setting in $scope"""
  }
}
