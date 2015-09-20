package org.jetbrains.sbt

import java.io.{File, FileWriter, PrintWriter}

import scala.io.Source

/**
 * @author Pavel Fatin
 */
object Loader {
  private val JavaVM = path(new File(new File(new File(System.getProperty("java.home")), "bin"), "java"))
  private val SbtLauncher = path(new File("sbt-launch.jar"))
  private val SbtPlugin = path(new File("extractor/target/scala-" + BuildInfo.scalaVersion + "/sbt-"+ BuildInfo.sbtVersionFull +"/classes/"))

  def load(project: File, resolveClassifiers: Boolean, sbtVersion: String, verbose: Boolean = false): Seq[String] = {
    val structureFile = createTempFile("sbt-structure", ".xml")
    val commandsFile = createTempFile("sbt-commands", ".lst")

    val opts = if (resolveClassifiers) Some("\"download resolveClassifiers resolveSbtClassifiers prettyPrint\"") else Some("\"download prettyPrint\"")

    writeLinesTo(commandsFile,
      "set artifactPath := file(\"" + path(structureFile) + "\")",
      "set artifactClassifier := " + opts,
      "apply -cp " + SbtPlugin + " org.jetbrains.sbt.ReadProject")

    val commands = Seq(JavaVM,
//      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
      "-Dsbt.log.noformat=true",
      "-Dsbt.version=" + sbtVersion,
      "-jar", SbtLauncher,
      "< " + path(commandsFile))

    run(commands, project, verbose)

    assert(structureFile.exists, "File must be created: " + structureFile.getPath)

    read(structureFile)
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
        if (it.startsWith("[error]")) process.destroy()
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
