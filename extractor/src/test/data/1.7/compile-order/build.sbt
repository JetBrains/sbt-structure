name := "some-name"
organization := "some-organization"
version := "1.2.3"
scalaVersion := "2.13.10"

lazy val core = project.in(file("core"))
  .settings(compileOrder := CompileOrder.JavaThenScala)

lazy val util = project.in(file("util"))
  .settings(compileOrder := CompileOrder.ScalaThenJava)
