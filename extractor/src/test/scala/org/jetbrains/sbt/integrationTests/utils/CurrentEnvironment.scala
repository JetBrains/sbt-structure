package org.jetbrains.sbt.integrationTests.utils

import org.jetbrains.sbt.integrationTests.utils.SbtProcessRunner.RunCommonOptions
import sbt.io.syntax.fileToRichFile

import java.io.File

object CurrentEnvironment {

  val OsName: String = System.getProperty("os.name").toLowerCase
  val UserHome: File = new File(System.getProperty("user.home")).getCanonicalFile.ensuring(_.exists())
  val WorkingDir: File = new File(".").getCanonicalFile

  val CurrentJavaHome: File = new File(System.getProperty("java.home"))
  val CurrentJavaExecutablePath: String = (CurrentJavaHome / "bin/java").getCanonicalPath

  private val PossibleJvmLocations: Seq[File] =
    if (OsName.contains("mac")) Seq(
      new File("/Library/Java/JavaVirtualMachines"),
      new File(s"${UserHome.getPath}/Library/Java/JavaVirtualMachines")
    )
    else if (OsName.contains("linux")) Seq(
      new File("/usr/lib/jvm"),
      new File("/usr/java")
    )
    else if (OsName.contains("win")) Seq(
      new File("C:\\Program Files\\Java"),
      new File("C:\\Program Files (x86)\\Java")
    )
    else
      throw new UnsupportedOperationException("Unknown operating system.")

  val JavaOldHome: File = findJvmInstallation("1.8")
    .orElse(findJvmInstallation("8"))
    .orElse(findJvmInstallation("11"))
    .getOrElse {
      throw new IllegalStateException(s"Java 1.8 or 11 not found in default locations:\n${PossibleJvmLocations.mkString("\n")}")
    }
  val JavaOldExecutablePath: String = (JavaOldHome / "bin/java").getCanonicalPath

  val SbtGlobalRoot: File = new File(UserHome, ".sbt-structure-global").getCanonicalFile

  println(
    s"""java home       : $CurrentJavaHome
       |java 8 home     : $JavaOldHome
       |sbt global root : $SbtGlobalRoot
       |see sbt-launcher logs in $SbtGlobalRoot/boot/update.log""".stripMargin
  )

  private def findJvmInstallation(javaVersion: String): Option[File] = {
    val jvmFolder = PossibleJvmLocations
      .flatMap { folder =>
        val dirs = Option(folder.listFiles()).getOrElse(Array.empty).filter(_.isDirectory)
        dirs.filter(_.getName.contains(javaVersion))
      }
      .headOption
      .map { root =>
        if (OsName.contains("mac"))
          root / "/Contents/Home"
        else
          root
      }

    jvmFolder.filter(_.exists())
  }

  def buildSbtRunCommonOptions(sbtVersionFull: Version, errorsExpected: Boolean = false): RunCommonOptions = {
    val sbtVersionShort = Version(PluginArtifactsUtils.sbtVersionBinary(sbtVersionFull.presentation))

    val sbtGlobalBase = SbtGlobalRoot / sbtVersionShort.presentation
    val sbtBootDir = SbtGlobalRoot / "boot/"
    val sbtIvyHome = SbtGlobalRoot / "ivy2/"
    val sbtCoursierHome = SbtGlobalRoot / "coursier/"

    RunCommonOptions(
      sbtVersion = sbtVersionFull,
      sbtVersionShort = sbtVersionShort,
      sbtGlobalBase = sbtGlobalBase,
      sbtBootDir = sbtBootDir,
      sbtIvyHome = sbtIvyHome,
      sbtCoursierHome = sbtCoursierHome,
      errorsExpected = errorsExpected
    )
  }
}
