sbtPlugin := true

name := "sbt-structure"

organization := "org.jetbrains"

version := "2.5.1" // Semantic Versioning

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalaVersion := "2.10.4"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.2" % "test"

addSbtPlugin("com.hanhuy.sbt" % "android-sdk-plugin" % "1.3.4")
