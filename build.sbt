import java.io.File
import CrossVersion.partialVersion
import sbt.Keys.{crossScalaVersions, scalaVersion}
import sbt.url
import xerial.sbt.Sonatype.GitHubHosting

def newProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(
      name := "sbt-structure-" + projectName,
      organization := "org.jetbrains.scala",
      unmanagedSourceDirectories in Compile +=
        baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",

      // Sonatype settings
      sonatypeProfileName := "org.jetbrains",
      homepage := Some(url("https://github.com/JetBrains/sbt-structure")),
      sonatypeProjectHosting := Some(GitHubHosting("JetBrains", "sbt-structure", "scala-developers@jetbrains.com")),
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
    )

def xmlArtifact(scalaVersion: String) =
  partialVersion(scalaVersion) match {
    // if scala 2.11+ is used, add dependency on scala-xml module
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      Seq("org.scala-lang.modules" %% "scala-xml" % "1.3.0")
    case _ =>
      Seq.empty
  }

lazy val core = newProject("core")
  .settings(
    libraryDependencies ++= xmlArtifact(scalaVersion.value),
    crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.11", "2.13.2")
  )

lazy val extractor = newProject("extractor")
  .settings(
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.googlecode.java-diff-utils" % "diffutils" % "1.2" % "test" withSources (),
      "org.specs2" %% "specs2-core" % "3.10.0" % "test",
      "org.specs2" %% "specs2-matcher-extra" % "3.10.0" % "test"
    ),
    // used only for testing, see publishVersions for versions that are actually used to publish artifacts
    crossSbtVersions := Nil, // handled by explicitly setting sbtVersion via scalaVersion
    crossScalaVersions := Seq("2.12.11", "2.10.7"),
    sbtVersion in pluginCrossBuild := {
      // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
      scalaBinaryVersion.value match {
        case "2.10" => "0.13.17"
        case "2.12" => "1.2.1"
      }
    },

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
        case Some((0, 13)) => Seq(sbt013_100_shared)
        case Some((1, _))  => Seq(sbt013_100_shared)
        case _             => Seq.empty[File]
      }
    },
  )
  .enablePlugins(TestDataDumper)

lazy val sbtStructure = project.in(file("."))
  .settings(
    // disable publishing in root project
    skip in publish := true,
    crossScalaVersions := Nil,
    crossSbtVersions := Nil,
    sonatypePublishTo := None
  )
  .aggregate(core, extractor)

lazy val testSetup = taskKey[Unit]("Setup tests for extractor")

// uncomment when we can upgrade to sbt 1.4+
//excludeLintKeys in Global += crossSbtVersions

val publishCoreCommand =
  "; project core ; ci-release"
val publishExtractorCommand =
  "; project extractor ; ci-release"
val publishAllCommand =
  "; reload ; project core ; ci-release ; project extractor ; ci-release "
val publishAllLocalCommand =
  "; reload ; project core ; + publishLocal ; project extractor ; + publishLocal"

// the ^ sbt-cross operator doesn't work that well for publishing, so we need to be more explicit about the command chain
addCommandAlias("publishCore", publishCoreCommand)
addCommandAlias("publishExtractor", publishExtractorCommand)
addCommandAlias("publishAll", publishAllCommand)
addCommandAlias("publishAllLocal", publishAllLocalCommand)
