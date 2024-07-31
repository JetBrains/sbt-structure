import sbt.librarymanagement.Configurations.{CompileInternal, RuntimeInternal, TestInternal}

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.3"

libraryDependencies ++= Seq(
  "junit" % "junit" % "4.13.2" % TestInternal,
  "org.scalacheck" %% "scalacheck" % "1.17.1" % CompileInternal ,
  "com.typesafe" % "config" % "1.4.3" % RuntimeInternal,
)
