import bintray.Keys._

sbtPlugin := true

name := "sbt-structure"

organization := "org.jetbrains"

version := "2.5.1" // Semantic Versioning

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalaVersion := "2.10.4" // be careful - version changes during cross-building(use portable code)

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.2" % "test"

publishMavenStyle := false

bintrayPublishSettings

repository in bintray := "sbt-plugins"

bintrayOrganization in bintray := Some("jetbrains")

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.13", "0.12")