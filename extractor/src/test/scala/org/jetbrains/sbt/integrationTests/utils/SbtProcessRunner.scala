package org.jetbrains.sbt.integrationTests.utils

import java.io.File
import scala.io.Source
import scala.util.Try

object SbtProcessRunner {

  case class ProcessRunResult(exitCode: Int, processOutput: String)

  case class RunCommonOptions(
    sbtVersion: Version,
    sbtVersionShort: Version,
    sbtGlobalBase: File,
    sbtBootDir: File,
    sbtIvyHome: File,
    // note, since 1.3 the coursier is used by default instead of Ivy
    sbtCoursierHome: File,
    verbose: Boolean = true,
    errorsExpected: Boolean = false
  )

  /**
   * @return sbt process output
   */
  def runSbtProcess(
    projectDir: File,
    sbtCommands: Seq[String],
    runOptions: RunCommonOptions
  ): ProcessRunResult = {
    val commandsFile = FileUtils.createTempFile("sbt-commands", ".lst")

    val sbtCommandsText = sbtCommands.mkString("\n")
    FileUtils.writeLinesTo(
      commandsFile,
      sbtCommandsText.linesIterator.toSeq *
    )

    val jvmExec =
      if (runOptions.sbtVersion >= Version("1.3.0")) CurrentEnvironment.CurrentJavaExecutablePath
      else CurrentEnvironment.JavaOldExecutablePath
    val commandLine: Seq[String] = Seq(
      jvmExec,
      s"-Dsbt.log.noformat=true",
      s"-Dsbt.version=${runOptions.sbtVersion}",

      // Use custom clean sbt setting dirs.
      // We do this to make the builds exactly reproducible and independent of user configuration
      s"-Dsbt.global.base=${runOptions.sbtGlobalBase}",
      s"-Dsbt.boot.directory=${runOptions.sbtBootDir}",

      // We need to add both sbt.ivy.home and ivy.home, otherwise some stuff can be still resolved in <home>/.ivy2
      // This issue is actual for sbt 1.1 and in 1.2. In 1.0 it works fine.
      // Since 1.3 Coursier is used instead of Ivy
      s"-Dsbt.ivy.home=${runOptions.sbtIvyHome}",
      s"-Divy.home=${runOptions.sbtIvyHome}",
      s"-Dsbt.coursier.home=${runOptions.sbtCoursierHome}",

      //Debug SBT process running structure extractor plugin
      //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5321",

      "-jar", SbtLauncherUtils.sbtLauncherPath,
      s"< ${path(commandsFile)}"
    )

    val envVars = Seq(
      // It's not enough to specify "-Dsbt.coursier.home" property,
      // sbt would still resolve some jars from the default coursier location also without setting these setting,
      // an error arise in sbt 1.3 on windows: https://github.com/sbt/sbt/issues/5386
      s"COURSIER_CONFIG_DIR=${runOptions.sbtCoursierHome}",
      s"COURSIER_DATA_DIR=${runOptions.sbtCoursierHome}/data",
      s"COURSIER_CACHE=${runOptions.sbtCoursierHome}/cache/v1",
      s"COURSIER_JVM_CACHE=${runOptions.sbtCoursierHome}/cache/jvm"
    )

    println(
      s"""SBT process command line:
         |${commandLine.mkString("\n").indented(spaces = 4)}
         |
         |SBT process environment variables:
         |${envVars.mkString("\n").indented(spaces = 4)}
         |
         |SBT commands file content:
         |${sbtCommandsText.linesIterator.map("  " + _).mkString("\n").indented(spaces = 4)}
         |""".stripMargin
    )

    runProcess(commandLine, projectDir, envVars, runOptions.verbose, runOptions.errorsExpected)
  }

  /**
   * @return process output
   */
  private def runProcess(
    commands: Seq[String],
    directory: File,
    envVars: Seq[String],
    verbose: Boolean,
    errorsExpected: Boolean
  ): ProcessRunResult = {
    val process = Runtime.getRuntime.exec(commands.toArray, envVars.toArray, directory)

    val processOutput: StringBuilder = new StringBuilder()

    val stdinThread = inThread {
      Source.fromInputStream(process.getInputStream).getLines().foreach { line =>
        processOutput.synchronized {
          processOutput.append(line).append("\n")
        }

        val hasError = line.startsWith("[error]")
        if (hasError && !errorsExpected) {
          System.err.println(line)
          process.destroy()
        }
        else if (verbose) {
          System.out.println("stdout: " + line)
        }
      }
    }

    val stderrThread = inThread {
      Source.fromInputStream(process.getErrorStream).getLines().foreach { line =>
        processOutput.synchronized {
          processOutput.append(line).append("\n")
        }
        if (verbose) {
          System.err.println("stderr: " + line)
        }
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

    ProcessRunResult(
      exitCode = process.exitValue(),
      processOutput = processOutput.toString
    )
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
}
