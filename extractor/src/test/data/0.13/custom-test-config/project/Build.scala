import sbt._
import Keys._

object MyBuild extends Build {
  lazy val FunTest = config("fun").extend(Test)

  lazy val bare = project.in(file("."))
    .configs(FunTest)
    .settings(inConfig(FunTest)(Defaults.testSettings):_*)
    .settings(
      name := "some-name",
      organization := "some-organization",
      version := "1.2.3",
      scalaVersion := "2.10.1",
      resolvers += JavaNet2Repository,
      libraryDependencies += "org.specs2" %% "specs2-core" % "2.4.14" % FunTest
    )
}
