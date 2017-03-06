name := "some-name"

organization := "some-organization"

version := "1.2.3"

scalaVersion := "0.1.1-20170108-add9a03-NIGHTLY"

scalaOrganization := "ch.epfl.lamp"

scalaBinaryVersion := "2.11"

ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))

libraryDependencies += "ch.epfl.lamp" % "dotty_2.11" % scalaVersion.value % "scala-tool"

scalaCompilerBridgeSource := ("ch.epfl.lamp" % "dotty-sbt-bridge" % scalaVersion.value % "component").sources()