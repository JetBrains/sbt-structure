package org.jetbrains.sbt

trait TestCompatible {
  def sbtVersion: String
  def sbtVersionFull: String
  def scalaVersion: String
}
