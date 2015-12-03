import bintray._
import bintray.Keys._

def newProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(
      name := "sbt-structure-" + projectName,
      organization := "org.jetbrains",
      version := "5.0.1",
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
      unmanagedSourceDirectories in Compile += baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
      publishMavenStyle := false
    )
    .settings(bintrayPublishSettings:_*)
    .settings(
      repository in bintray := "sbt-plugins",
      bintrayOrganization in bintray := Some("jetbrains"),
      credentialsFile in bintray := file(".credentials")
    )

val core = newProject("core")
  .settings(
    libraryDependencies ++= {
      if (scalaVersion.value == "2.11.6")
        Seq("org.scala-lang.modules" % "scala-xml_2.11" % "1.0.3")
      else
        Seq.empty
    },
    crossScalaVersions := Seq("2.9.2", "2.10.4", "2.11.6")
  )

val testSetup = taskKey[Unit]("Setup tests for extractor")

val extractor = newProject("extractor")
  .settings(crossBuildingSettings:_*)
  .settings(
    name := name.value + "-" + CrossBuilding.pluginSbtVersion.value,
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.googlecode.java-diff-utils" % "diffutils" % "1.2" withSources(),
      "org.specs2" %% "specs2" % "1.12.3" % "test"),
    CrossBuilding.crossSbtVersions := Seq("0.12.4", "0.13.0", "0.13.7", "0.13.9"),
    testSetup := {
      System.setProperty("structure.sbtversion.full", CrossBuilding.pluginSbtVersion.value)
      System.setProperty("structure.sbtversion.short", CrossBuilding.pluginSbtVersion.value.substring(0, 4))
      System.setProperty("structure.scalaversion", scalaBinaryVersion.value)
    },
    test in Test <<= (test in Test).dependsOn(testSetup),
    testOnly in Test <<= (testOnly in Test).dependsOn(testSetup),
    name in bintray := "sbt-structure-extractor"
  )

val sbtStructure = project.in(file(".")).aggregate(core, extractor)
