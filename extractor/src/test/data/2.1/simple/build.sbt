import sbt.ThisBuild

name := "some-name"
organization := "some-organization"
version := "1.2.3"

val root = (project in file("."))
  .settings(
    scalaVersion := "3.6.2",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test
    )
  )
