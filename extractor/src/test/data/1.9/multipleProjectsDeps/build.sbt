ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val foo = (project in file("foo"))
lazy val dummy = (project in file("dummy"))
lazy val root = (project in file("."))
.dependsOn(foo, dummy)