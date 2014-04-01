import bintray.Keys._

sbtPlugin := true

name := "sbt-structure"

organization := "org.jetbrains"

version := "2.3.2" // Semantic Versioning

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.2" % "test"

publishMavenStyle := false

bintrayPublishSettings

repository in bintray := "sbt-plugins"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

bintrayOrganization in bintray := Some("jetbrains")
