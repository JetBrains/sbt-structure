package org.jetbrains.sbt.integrationTests.utils

import java.io.File
import java.util.Properties

object PluginArtifactsUtils {

  /**
   * @return version of the locally-published plugin
   */
  //noinspection ScalaUnusedSymbol
  lazy val publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions: String = {
    println("Publishing sbt-idea-plugin to local repository and getting it's version")

    val currentDateAndTime = new java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new java.util.Date())
    val testVersion = s"0.1-version-for-tests-$currentDateAndTime"

    val sbtVersion = sbtVersionIn(CurrentEnvironment.WorkingDir).get
    val outputLines = SbtProcessRunner.runSbtProcess(
      projectDir = CurrentEnvironment.WorkingDir,
      sbtCommands = Seq(s"""set (ThisBuild / version) := "$testVersion" ; project extractor ; +publishLocal ; show version"""),
      runOptions = CurrentEnvironment.buildSbtRunCommonOptions(sbtVersion),
    ).processOutput.linesIterator.toSeq

    testVersion
  }

  def sbtVersionIn(directory: File): Option[Version] = {
    val propertiesFile = sbtBuildPropertiesFile(directory)
    if (propertiesFile.exists())
      readPropertyFrom(propertiesFile, "sbt.version").map(Version(_))
    else
      None
  }

  private def sbtBuildPropertiesFile(base: File): File =
    new File(base, "project/build.properties")

  private def readPropertyFrom(file: File, name: String): Option[String] = {
    sbt.io.Using.fileInputStream(file) { input =>
      val properties = new Properties()
      properties.load(input)
      Option(properties.getProperty(name))
    }
  }

  // 1.10.7 => 1.10
  // 2.0.0-M3 => 2.0
  def sbtVersionBinary(sbtVersionFull: String): String = {
    sbtVersionFull.split('.') match {
      case Array(a, b, _) => s"$a.$b" // e.g. 0.13, 1.0, 1.5
    }
  }

  def pluginSbtCrossVersionBinary(sbtVersion: Version): String =
    if (sbtVersion < Version("1.0")) "0.13"
    else if (sbtVersion < Version("1.3")) "1.0"
    else if (sbtVersion.presentation.startsWith("1")) "1.3"
    else "2"

  def getPluginUnpublishedClassesDirectory(sbtVersionShort: Version): File = {
    val scalaVersion = scalaVersionUsedBySbt(sbtVersionShort)
    val sbtCrossVersion = pluginSbtCrossVersionBinary(sbtVersionShort)
    val moduleName: String = if (sbtVersionShort < Version("1.0")) s"extractor-legacy-0.13" else s"extractor"
    val filePath: String = s"$moduleName/target/scala-$scalaVersion/sbt-$sbtCrossVersion/classes/"
    val file = new File(filePath).getCanonicalFile
    assert(file.exists(), s"Plugin file does not exist: $file.\nEnsure to build the plugin fist by running 'sbt +compile'")
    file
  }

  private def scalaVersionUsedBySbt(sbtVersion: Version): String =
    sbtVersion.presentation.split('.') match {
      case Array("0", "13") => "2.10"
      case Array("1", _) => "2.12"
      case Array("2", _) => "3.7.3"
      case _ => throw new IllegalArgumentException(s"sbt version not supported by this test: $sbtVersion")
    }
}
