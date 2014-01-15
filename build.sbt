sbtPlugin := true

name := "sbt-structure"

organization := "org.jetbrains"

version := "2.1.0" // Semantic Versioning

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.2" % "test"

publishTo := Some(Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))

publishMavenStyle := false

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
