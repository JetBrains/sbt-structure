ThisBuild / scalaVersion := "2.13.6"

lazy val root = (project in file("."))

lazy val moduleA = (project in file("moduleA"))

lazy val moduleB = (project in file("moduleB"))
  .settings(
    bspEnabled := false
  )
