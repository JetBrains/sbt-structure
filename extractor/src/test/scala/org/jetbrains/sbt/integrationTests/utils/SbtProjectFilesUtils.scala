package org.jetbrains.sbt.integrationTests.utils

import sbt.fileToRichFile

import java.io.File
import java.nio.file.Path
import scala.io.Source

object SbtProjectFilesUtils {

  def updateSbtStructurePluginToVersion(
    projectDir: File,
    pluginVersion: String,
    sbtVersionFull: Version
  ): Path = {
    val pluginsSbtFile = projectDir / "project" / "plugins.sbt"

    if (!pluginsSbtFile.exists()) {
      // ensure the file exists
      pluginsSbtFile.getParentFile.mkdirs()
      pluginsSbtFile.createNewFile()
    }

    val content = FileUtils.readLines(pluginsSbtFile)
    val contentWithoutPlugin = content
      .filterNot(_.contains("""addSbtPlugin("org.jetbrains.scala" % "sbt-structure-extractor""""))
      .mkString("\n")

    val sbtCrossVersion = PluginArtifactsUtils.pluginSbtCrossVersionBinary(sbtVersionFull)
    val contentUpdated =
      s"""$contentWithoutPlugin
         |addSbtPlugin("org.jetbrains.scala" % "sbt-structure-extractor" % "$pluginVersion", "$sbtCrossVersion")
         |""".stripMargin.trim

    FileUtils.writeStringToFile(pluginsSbtFile, contentUpdated)

    pluginsSbtFile.toPath
  }
}