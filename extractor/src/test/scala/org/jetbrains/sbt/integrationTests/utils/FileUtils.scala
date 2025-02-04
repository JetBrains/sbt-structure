package org.jetbrains.sbt.integrationTests.utils

import java.io.{File, FileWriter, PrintWriter}
import java.nio.file.Files
import scala.io.Source

object FileUtils {

  def read(file: File): String =
    readLines(file).mkString("\n")

  def readLines(file: File): Seq[String] =
    sbt.io.Using.fileInputStream(file) { in =>
      Source.fromInputStream(in).getLines().toArray.toSeq
    }

  def createTempFile(prefix: String, suffix: String): File = {
    val file = Files.createTempFile(prefix, suffix).toFile
    file.deleteOnExit()
    file
  }

  def createTempDirectory(prefix: String): File = {
    val file = Files.createTempDirectory(prefix).toFile
    file.deleteOnExit()
    file
  }

  def writeStringToFile(file: File, content: String): Unit = {
    val writer = new PrintWriter(new FileWriter(file))
    writer.write(content)
    writer.close()
  }

  def writeLinesTo(file: File, lines: String*): Unit = {
    val writer = new PrintWriter(new FileWriter(file))
    lines.foreach(writer.println)
    writer.close()
  }
}
