ThisBuild / scalaVersion := "3.6.2"

lazy val root = (project in file("."))

lazy val moduleA = (project in file("moduleA"))

lazy val moduleB = (project in file("moduleB"))
  .settings(
    Test / bspEnabled := false
  )
