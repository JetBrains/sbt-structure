sbtPlugin := true

name := "sbt-structure"

organization := "org.jetbrains"

version := "1.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.2" % "test"

publishTo := Some(Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))

publishMavenStyle := false