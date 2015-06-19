package org.jetbrains

import java.io.File

import scala.io.Source

/**
 * @author Pavel Fatin
 */
package object sbt {
  def read(file: File): Seq[String] = {
    val source = Source.fromFile(file)
    try {
      source.getLines().toList
    } finally {
      source.close()
    }
  }
}
