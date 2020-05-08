package org.jetbrains.sbt

import java.io.{File, FileWriter, PrintWriter}
import java.net.URL

import sbt.jetbrains.apiAdapter._

import scala.io.Source

/**
  * @author Pavel Fatin
  * @author Nikolay Obedin
  */
object Loader {
  private val JavaVM = path(
    new File(new File(new File(System.getProperty("java.home")), "bin"), "java")
  )

  private def sbtLauncher = {
    val launcher = new File("sbt-launch.jar")

    if (!launcher.exists())
      Using.urlInputStream(
        new URL(
          "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/1.3.10/sbt-launch-1.3.10.jar"
        )
      ) { in =>
        IO.transfer(in, launcher)
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
           verbose: Boolean = true): String = {
    val structureFile = createTempFile("sbt-structure", ".xml")
    val commandsFile = createTempFile("sbt-commands", ".lst")

    val opts = "download prettyPrint " + options

    writeLinesTo(
      commandsFile,
      "set SettingKey[Option[File]](\"sbtStructureOutputFile\") in Global := Some(file(\"" + path(
        structureFile
      ) + "\"))",
      "set SettingKey[String](\"sbtStructureOptions\") in Global := \"" + opts + "\"",
      "apply -cp " + path(pluginFile) + " org.jetbrains.sbt.CreateTasks",
      "*/*:dumpStructure"
    )

    val commands = Seq(
      JavaVM,
//      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
      "-Dsbt.log.noformat=true",
      "-Dsbt.version=" + sbtVersion,
      // to make the builds exactly reproducible and independent of user configuration,
      // use custom clean sbt setting dirs
      "-Dsbt.global.base=" + sbtGlobalBase,
      "-Dsbt.boot.directory=" + sbtBootDir,
      "-Dsbt.ivy.home=" + sbtIvyHome,
      "-jar",
      sbtLauncher,
      "< " + path(commandsFile)
    )

    run(commands, project, verbose)

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

  private def writeLinesTo(file: File, lines: String*) {
    val writer = new PrintWriter(new FileWriter(file))
    lines.foreach(writer.println)
    writer.close()
  }

  private def run(commands: Seq[String], directory: File, verbose: Boolean) {
    val process = Runtime.getRuntime.exec(commands.toArray, null, directory)

    val stdinThread = inThread {
      Source.fromInputStream(process.getInputStream).getLines().foreach { it =>
        if (verbose) System.out.println("stdout: " + it) else ()
        if (it.startsWith("[error]")) {
          System.out.println(s"error running sbt in $directory:\n" + it)
          process.destroy()
        }
      }
    }

    val stderrThread = inThread {
      Source.fromInputStream(process.getErrorStream).getLines().foreach { it =>
        if (verbose) System.err.println("stderr: " + it) else ()
      }
    }

    process.waitFor()

    stdinThread.join()
    stderrThread.join()
  }

  private def inThread(block: => Unit): Thread = {
    val runnable = new Runnable {
      def run() {
        block
      }
    }
    val thread = new Thread(runnable)
    thread.start()
    thread
  }
}
