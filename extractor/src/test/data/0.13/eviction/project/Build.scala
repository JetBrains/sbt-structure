import sbt._
import Keys._

object MyBuild extends Build {
  lazy val childOne = project
    .settings(
      libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.3.0"
    )

  lazy val childTwo = project
    .dependsOn(childOne)
    .settings(
      libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.5.0"
    )

  lazy val eviction = project.in(file(".")).aggregate(childOne, childTwo)
}