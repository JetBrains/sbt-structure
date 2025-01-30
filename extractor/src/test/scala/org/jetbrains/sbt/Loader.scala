package org.jetbrains.sbt

import java.io.{File, FileWriter, PrintWriter}
import java.net.URL
import scala.io.Source
import scala.util.Try

object Loader {
  private val JavaVM = path(
    new File(new File(new File(System.getProperty("java.home")), "bin"), "java")
  )

  private def sbtLauncher = {
    //isolate the launcher in it's own folder (otherwise target from this project can be somehow used)
    val launcher = new File("sbt-launcher/sbt-launch.jar")

    if (!launcher.exists())
      sbt.io.Using.urlInputStream(
        new URL(
          "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/1.3.10/sbt-launch-1.3.10.jar"
        )
      ) { in =>
        sbt.io.IO.transfer(in, launcher)
      }

    path(launcher)
  }

  def load(project: File,
           options: String,
           sbtVersion: String,
           pluginFile: File,
           sbtGlobalBase: File,
           sbtBootDir: File,
           sbtIvyHome: File,
           sbtCoursierHome: File, // since 1.3 coursier is used by default instead of Ivy
           verbose: Boolean = true): String = {
    val structureFile = createTempFile("sbt-structure", ".xml")
    val commandsFile = createTempFile("sbt-commands", ".lst")

    val opts = "download prettyPrint " + options

    val sbtVersionSupportsSlashSyntax = sbtVersion.startsWith("2") || sbtVersion.startsWith("1") &&
      !sbtVersion.startsWith("1.0")
    val commandsText = if (sbtVersionSupportsSlashSyntax)
      s"""set Global / SettingKey[Option[File]]("sbtStructureOutputFile") := Some(file("${path(structureFile)}"))
         |set Global/  SettingKey[String]("sbtStructureOptions") := "$opts"
         |apply -cp ${path(pluginFile)} org.jetbrains.sbt.CreateTasks
         |dumpStructure
         |""".stripMargin.trim
    else
      s"""set SettingKey[Option[File]]("sbtStructureOutputFile") in Global := Some(file("${path(structureFile)}"))
         |set SettingKey[String]("sbtStructureOptions") in Global := "$opts"
         |apply -cp ${path(pluginFile)} org.jetbrains.sbt.CreateTasks
         |dumpStructure
         |""".stripMargin.trim

    writeLinesTo(
      commandsFile,
      commandsText.linesIterator.toSeq *
    )

    val commands = Seq(
      JavaVM,
      //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
      s"-Dsbt.log.noformat=true",
      s"-Dsbt.version=$sbtVersion",
      // to make the builds exactly reproducible and independent of user configuration,
      // use custom clean sbt setting dirs
      s"-Dsbt.global.base=$sbtGlobalBase",
      s"-Dsbt.boot.directory=$sbtBootDir",

      // We need to add both sbt.ivy.home and ivy.home, otherwise some stuff can be still resolved in <home>/.ivy2
      // This issue is actual for sbt 1.1 and 1.2, in 1.0 it works fine.
      // Since 1.3 coursier is used instead of Ivy
      s"-Dsbt.ivy.home=$sbtIvyHome",
      s"-Divy.home=$sbtIvyHome",
      s"-Dsbt.coursier.home=$sbtCoursierHome",

      //Debug SBT process running structure extractor plugin
      //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5321",

      "-jar", sbtLauncher,
      s"< ${path(commandsFile)}"
    )

    val envVars = Seq(
      // It's not enough to specify "-Dsbt.coursier.home" property,
      // sbt would still resolve some jars from default coursier location
      // also without setting these setting an error arise in sbt 1.3 on windows: https://github.com/sbt/sbt/issues/5386
      s"COURSIER_CONFIG_DIR=$sbtCoursierHome",
      s"COURSIER_DATA_DIR=$sbtCoursierHome/data",
      s"COURSIER_CACHE=$sbtCoursierHome/cache/v1",
      s"COURSIER_JVM_CACHE=$sbtCoursierHome/cache/jvm"
    )

    println(
      s"""SBT process command line:
         |${commands.mkString("\n").indented(spaces = 4)}
         |
         |SBT process environment variables:
         |${envVars.mkString("\n").indented(spaces = 4)}
         |
         |SBT commands file content:
         |${commandsText.linesIterator.map("  " + _).mkString("\n").indented(spaces = 4)}
         |""".stripMargin
    )

    run(commands, project, envVars, verbose)

    assert(
      structureFile.exists,
      "File must be created: " + structureFile.getPath
    )

    val structureString = TestUtil.read(structureFile)
    assert(
      structureString.nonEmpty,
      "structure dump was empty for project " + project.getPath
    )
    structureString
  }

  private def path(file: File): String = file.getAbsolutePath.replace('\\', '/')

  private def createTempFile(prefix: String, suffix: String): File = {
    val file = File.createTempFile(prefix, suffix)
    file.deleteOnExit()
    file
  }

  private def writeLinesTo(file: File, lines: String*): Unit = {
    val writer = new PrintWriter(new FileWriter(file))
    lines.foreach(writer.println)
    writer.close()
  }

  private def run(commands: Seq[String], directory: File, envVars: Seq[String], verbose: Boolean): Unit = {
    val process = Runtime.getRuntime.exec(commands.toArray, envVars.toArray, directory)

    val stdinThread = inThread {
      Source.fromInputStream(process.getInputStream).getLines().foreach { line =>
        val hasError = line.startsWith("[error]")
        if (hasError) {
          System.err.println(line)
          process.destroy()
        }
        else if (verbose) {
          System.out.println("stdout: " + line)
        }
      }
    }

    val stderrThread = inThread {
      Source.fromInputStream(process.getErrorStream).getLines().foreach { it =>
        if (verbose) System.err.println("stderr: " + it) else ()
      }
    }

    Runtime.getRuntime.addShutdownHook(new Thread((() => {
      if (process.isAlive) {
        System.err.print("Destroying dangling sbt process")
        Try(process.destroy())
      }
    }): Runnable, "terminate sbt process"))

    println(s"SBT process pid: ${process.pid()}")
    process.waitFor()

    stdinThread.join()
    stderrThread.join()
  }

  private def inThread(block: => Unit): Thread = {
    val runnable = new Runnable {
      def run(): Unit = {
        block
      }
    }
    val thread = new Thread(runnable)
    thread.start()
    thread
  }

  implicit class StringOps(val text: String) extends AnyVal {
    def indented(spaces: Int): String = {
      val indent = " " * spaces
      text.linesIterator.map(indent + _).mkString("\n")
    }
  }
}
