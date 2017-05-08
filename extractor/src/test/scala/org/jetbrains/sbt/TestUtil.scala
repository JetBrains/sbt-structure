package org.jetbrains.sbt

import java.io.File

import scala.io.Source

/**
 * @author Pavel Fatin
 */
object TestUtil {
  def read(file: File): String = {
    val source = Source.fromFile(file)
    try {
      source.getLines().mkString("\n")
    } finally {
      source.close()
    }
  }
}
