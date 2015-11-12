package org.jetbrains.sbt

import java.io.File

import scala.io.Source

/**
 * @author Pavel Fatin
 */
object TestUtil {
  def read(file: File): Seq[String] = {
    val source = Source.fromFile(file)
    try {
      source.getLines().toList
    } finally {
      source.close()
    }
  }
}
