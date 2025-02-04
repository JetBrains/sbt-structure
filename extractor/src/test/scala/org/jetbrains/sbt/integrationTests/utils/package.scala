package org.jetbrains.sbt.integrationTests

import java.io.File

package object utils {
  def path(file: File): String = file.getAbsolutePath.replace('\\', '/')

  implicit class StringOps(private val text: String) extends AnyVal {
    def indented(spaces: Int): String = {
      val indent = " " * spaces
      text.linesIterator.map(indent + _).mkString("\n")
    }

    /**
     * Normalize path separator to easier test on Windows
     *
     * ATTENTION: this affects all backslashes, not only in file paths
     */
    def normalizeFilePathSeparatorsInXml: String =
      text.replace("\\", "/")
  }
}
