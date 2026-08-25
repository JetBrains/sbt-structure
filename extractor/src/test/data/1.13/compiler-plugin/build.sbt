name := "some-name"
organization := "some-organization"
version := "1.2.3"

val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.16",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
