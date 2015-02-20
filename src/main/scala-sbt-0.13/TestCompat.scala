package org.jetbrains.sbt
object TestCompat extends TestCompatible {
  lazy val sbtVersion = System.getProperty("structure.sbtversion.short")
  lazy val sbtVersionFull = System.getProperty("structure.sbtversion.full")
  lazy val scalaVersion = System.getProperty("structure.scalaversion")
}
