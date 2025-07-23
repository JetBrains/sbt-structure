ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val buzz = (project in file("buzz"))
lazy val foo = (project in file("foo"))
  .dependsOn(buzz)
lazy val dummy = (project in file("dummy"))
lazy val root = (project in file("."))
.dependsOn(foo, dummy)