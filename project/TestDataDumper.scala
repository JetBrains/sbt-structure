import java.io.File

import sbt.{Def, _}
import Keys._
import sbt.plugins.JvmPlugin

import scala.io.Source
import scala.util.Try


object TestDataDumper extends AutoPlugin {

  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = noTrigger

  private val javaVM = file(System.getProperty("java.home")) / "bin" / "java"

  private def path(file: File): String = file.getCanonicalPath.replace('\\', '/')
  private def canon(path: String): String = path.stripSuffix("/").stripSuffix("\\")

  object autoImport {
    val dumpStructure013: TaskKey[Seq[File]] = taskKey[Seq[File]]("dump xml output for running current version of sbt-structure on all of the 0.13 test data directories")
    val sbtLauncher: SettingKey[File] = settingKey[File]("where is the sbt launcher hidden")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    dumpStructure013 := dumpStructure013Task.value
  )

  override def buildSettings: Seq[Def.Setting[_]] = Seq(
    sbtLauncher := baseDirectory.value / "sbt-launch.jar"
  )

  val dumpStructure013Task: Def.Initialize[Task[Seq[File]]] = Def.task {
    import sbt.NameFilter._

    val pluginJar = (packagedArtifact in (Compile,packageBin)).value._2
    val testDataDirs = ((sourceDirectory in Test).value / "data" / "0.13").listFiles.filter(_.isDirectory).toSeq
    val nameFilter = (name: String) => name.matches("structure-[0-9.]+\\.xml")

    for {
      testDir <- testDataDirs
      sbtVer <- (testDir ** nameFilter).get.map(_.name.stripPrefix("structure-").stripSuffix(".xml"))
      dumpFile <- {
        streams.value.log.info(s"dumping structure xml for $testDir")
        dumpStructure(sbtVer, version.value, sbtLauncher.value, pluginJar, testDir).toOption
      }
    } yield dumpFile
  }

  def dumpStructure(sbtVersion: String, sbtStructureVersion: String, sbtLauncherJar: File, pluginJar: File, workingDir: File): Try[File] = {

    val userHome = path(file(System.getProperty("user.home")))
    val androidHome = Option(System.getenv.get("ANDROID_HOME")).map(new File(_).getCanonicalFile)
    val sbtGlobalRoot = file(System.getProperty("user.home")) / ".sbt-structure-global/"
    val sbtBootDir = path(sbtGlobalRoot / "boot/")
    val sbtIvyHome = path(sbtGlobalRoot / "ivy2/")
    val sbtGlobalBase = path(new File(sbtGlobalRoot, sbtVersion).getCanonicalFile)
    val structureFile = workingDir / s"structure-$sbtVersion-dump.xml"
    val defaultOpts = "download prettyPrint resolveClassifiers resolveSbtClassifiers resolveJavadocs"
    val optsFile = workingDir / "dumpOptions"
    val opts =
      if (optsFile.exists) Source.fromFile(optsFile).getLines().mkString(" ").trim
      else defaultOpts

    val commands = Seq(
      s"""set Seq(SettingKey[Option[File]]("sbt-structure-output-file") in Global := Some(file("${path(structureFile)}")), SettingKey[String]("sbt-structure-options") in Global := "$opts")""",
      s"apply -cp ${path(pluginJar)} org.jetbrains.sbt.CreateTasks",
      "*/*:dump-structure"
    )

    IO.withTemporaryFile("commands","") { commandsFile =>
      IO.writeLines(commandsFile, commands)

      val commandLine = Seq(path(javaVM),
        //      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
        "-Dsbt.log.noformat=true",
        "-Dsbt.version=" + sbtVersion,
        // to make the builds exactly reproducible and independent of user configuration,
        // use custom clean sbt setting dirs
        "-Dsbt.global.base=" + sbtGlobalBase,
        "-Dsbt.boot.directory=" + sbtBootDir,
        "-Dsbt.ivy.home=" + sbtIvyHome,
        "-jar", path(sbtLauncherJar)
      )

      val dump = Process(commandLine, workingDir) #< commandsFile
      Try(require(dump.! == 0, s"sbt dump failed on $workingDir")).map { _ =>
        // map local paths to variables used in tests
        replaceVariables(structureFile, sbtVersion, workingDir, userHome, androidHome, sbtGlobalRoot, sbtBootDir, sbtIvyHome)

        val newStructureFile = structureFile.getParentFile / s"structure-$sbtVersion.xml"
        IO.move(structureFile, newStructureFile)
        newStructureFile
      }
    }
  }

  // duplicates some functionality in ImportSpec
  def replaceVariables(structureFile: File,
                       sbtVersion: String,
                       base: File,
                       userHome: String,
                       androidHome: Option[File],
                       sbtGlobalRoot: File,
                       sbtBootDir: String,
                       sbtIvyHome: String
                      ): Unit = {

    val replaced = Source.fromFile(structureFile).getLines().mkString("\n")
      .replace(base.getCanonicalFile.toURI.toString, "$URI_BASE")
      .replace(base.getCanonicalPath, "$BASE")
      .replace(androidHome.map(p => canon(p.toURI.toString)).getOrElse(""), "$URI_ANDROID_HOME")
      .replace(androidHome.map(p => path(p)).getOrElse(""), "$ANDROID_HOME")
      .replace(sbtIvyHome, "$IVY2")
      .replace(sbtBootDir, "$SBT_BOOT")
      .replace(userHome, "$HOME")

    IO.write(structureFile, replaced)
  }
}
