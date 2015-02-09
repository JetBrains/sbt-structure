import bintray.Keys._

sbtPlugin := true

name := "sbt-structure"

organization := "org.jetbrains"

version := "3.0.0" // Semantic Versioning

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalaVersion := "2.10.4" // be careful - version changes during cross-building(use portable code)

libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils" % "1.2" withSources()

libraryDependencies += "org.specs2" %% "specs2" % "1.12.3" % "test"

publishMavenStyle := false

bintrayPublishSettings

repository in bintray := "sbt-plugins"

bintrayOrganization in bintray := Some("jetbrains")

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.13.0", "0.12.4")

lazy val setSbtVersionForTests = taskKey[Unit]("sets sbt and scala version properties for use in cross-testing")

setSbtVersionForTests := {
  System.setProperty("structure.sbtversion.full", CrossBuilding.pluginSbtVersion.value)
  System.setProperty("structure.sbtversion.short", CrossBuilding.pluginSbtVersion.value.substring(0, 4))
  System.setProperty("structure.scalaversion", scalaBinaryVersion.value)
}

test in Test := {
  setSbtVersionForTests.value
  (test in Test).value
}

