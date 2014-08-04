package org.jetbrains.sbt

import java.io.{PrintWriter, FileWriter, File}
import scala.io.Source

/**
 * @author Pavel Fatin
 */
object Loader {
  private val JavaVM = path(new File(new File(new File(System.getProperty("java.home")), "bin"), "java"))
  private val SbtLauncher = path(new File("sbt-launch.jar"))
  private val SbtVersion = "0.13.0"
  private val FullClasspath = {
    // use full classpath to account for dependencies, e.g. android-sdk-plugin
    val urls = getClass.getClassLoader.asInstanceOf[java.net.URLClassLoader].getURLs
    urls.map(_.getPath).mkString(";")
  }

  def load(project: File, download: Boolean): Seq[String] = {
    val structureFile = createTempFile("sbt-structure", ".xml")
    val commandsFile = createTempFile("sbt-commands", ".lst")

    val className = if (download) "ReadProjectAndRepository" else "ReadProject"

    writeLinesTo(commandsFile,
      s"""set artifactPath := file("${path(structureFile)}")""",
      s"""apply -cp $FullClasspath org.jetbrains.sbt.$className""")

    val commands = Seq(JavaVM,
//      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
      s"-Dsbt.version=$SbtVersion",
      "-jar", SbtLauncher,
      "< " + path(commandsFile))

    run(commands, project)

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
    lines.foreach(writer.println(_))
    writer.close()
  }

  private def run(commands: Seq[String], directory: File) {
    val process = Runtime.getRuntime.exec(commands.toArray, null, directory)

    val stdinThread = inThread {
      Source.fromInputStream(process.getInputStream).getLines().foreach { it =>
        System.out.println("stdout: " + it)
      }
    }

    val stderrThread = inThread {
      Source.fromInputStream(process.getErrorStream).getLines().foreach { it =>
        System.err.println("stderr: " + it)
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
