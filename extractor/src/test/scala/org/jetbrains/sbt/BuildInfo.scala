package org.jetbrains.sbt

/**
 * @author Nikolay Obedin
 * @since 4/7/15.
 */

object BuildInfo {
  val sbtVersion = System.getProperty("structure.sbtversion.short")
  val sbtVersionFull = System.getProperty("structure.sbtversion.full")
  val scalaVersion = System.getProperty("structure.scalaversion")
}

