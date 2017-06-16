import java.io.File

import CrossBuildingPlugin.autoImport.sbtVersionSeries
import CrossVersion.partialVersion
import sbt.Keys.scalaVersion

def newProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(
      name := "sbt-structure-" + projectName,
      organization := "org.jetbrains",
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
      unmanagedSourceDirectories in Compile +=
        baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
      publishMavenStyle := false
    )
    .settings(
      bintrayRepository := "sbt-plugins",
      bintrayOrganization := Some("jetbrains"),
      bintrayCredentialsFile := file(".credentials")
    )


def specsArtifact(scalaVersion: String) =
  partialVersion(scalaVersion) match {
    case Some((2,9)) | Some((2,10)) => "org.specs2" %% "specs2" % "1.12.3" % "test"
    case _ => "org.specs2" %% "specs2-core" % "3.8.9" % "test"
  }

def xmlArtifact(scalaVersion: String) =
  partialVersion(scalaVersion) match {
    // if scala 2.11+ is used, add dependency on scala-xml module
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.5")
    case _ =>
      Seq.empty
  }

lazy val core = newProject("core")
  .settings(
    libraryDependencies ++= xmlArtifact(scalaVersion.value),
    crossScalaVersions := Seq("2.10.6", "2.11.9", "2.12.2")
  )

lazy val extractor = newProject("extractor")
  .settings(
    sbtPlugin := true,

    scalaVersion := (sbtVersionSeries.value match {
      case Sbt012 => "2.9.2"
      case Sbt013 => "2.10.6"
      case Sbt1 => "2.12.2"
    }),

    libraryDependencies ++= Seq(
      "com.googlecode.java-diff-utils" % "diffutils" % "1.2" % "test" withSources(),
      specsArtifact(scalaVersion.value)
    ),

    crossSbtVersions := Seq("0.13.0", "0.13.9", "0.13.13"),

    testSetup := {
      System.setProperty("structure.sbtversion.full", (sbtVersion in pluginCrossBuild).value)
      System.setProperty("structure.sbtversion.short", (sbtBinaryVersion in pluginCrossBuild).value)
      System.setProperty("structure.scalaversion", scalaBinaryVersion.value)
    },
    test in Test := (test in Test).dependsOn(testSetup).value,
    testOnly in Test := (testOnly in Test).dependsOn(testSetup).evaluated,
    name in bintray := "sbt-structure-extractor",
    scalacOptions ++= Seq("-deprecation"),

    sources in Compile := {
      val sbtVer = (sbtVersion in pluginCrossBuild).value
      val srcs = (sources in Compile).value
      // remove the AutoPlugin since it doesn't compile when testing for sbt 0.13.0
      // it's okay to compile it into the jar, old sbt won't know about it!
      if (sbtVer == "0.13.0")
        srcs.filterNot(_.getName == "StructurePlugin.scala")
      else srcs
    },

    // I want to share source between 0.13 and 1.0, but not 0.12
    unmanagedSourceDirectories in Compile ++= {
      val sbt013_100_shared = (sourceDirectory in Compile).value / "scala-sbt-0.13-1.0"
      partialVersion((sbtVersion in pluginCrossBuild).value) match {
        case Some((0,13)) => Seq(sbt013_100_shared)
        case Some((1, 0)) => Seq(
          sbt013_100_shared,
          (sourceDirectory in Compile).value / "scala-sbt-1.0" // workaround for https://github.com/sbt/sbt/issues/3217
        )
        case _ => Seq.empty[File]
      }
    }

  )
  .enablePlugins(TestDataDumper)

lazy val sbtStructure = project.in(file(".")).aggregate(core, extractor)

lazy val testSetup = taskKey[Unit]("Setup tests for extractor")

val publishVersions = Seq("0.13.13", "1.0.0-M6")
val publishAllCommand =
  "; reload ; project core ; + publish ; project extractor " +
    publishVersions.map(v => s"; reload ; ^^ $v publish ").mkString
val publishAllLocalCommand =
  "; reload ; project core ; + publishLocal ; project extractor " +
    publishVersions.map(v => s"; reload ; ^^ $v publishLocal ").mkString

// the ^ sbt-cross operator doesn't work that well for publishing, so we need to be more explicit about the command chain
addCommandAlias("publishAll_012","; reload ; project core ; ++ 2.9.2 publish ; project extractor ; ^^ 0.12.4 publish")
addCommandAlias("publishAllLocal_012", "; reload ; project core ; ++ 2.9.2 publishLocal ; project extractor ; ^^ 0.12.4 publishLocal")
addCommandAlias("publishAll", publishAllCommand)
addCommandAlias("publishAllLocal", publishAllLocalCommand)
