import CrossVersion.partialVersion
import sbt.Keys.{crossScalaVersions, excludeLintKeys, scalaVersion}
import sbt.url
import xerial.sbt.Sonatype.GitHubHosting

def newProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(
      name := "sbt-structure-" + projectName,
      organization := "org.jetbrains.scala",
      Compile / unmanagedSourceDirectories +=
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

val scala210 = "2.10.7"
val scala212 = "2.12.17"

lazy val core = newProject("core")
  .settings(
    libraryDependencies ++= xmlArtifact(scalaVersion.value),
    crossScalaVersions := Seq(scala210, "2.11.12", scala212, "2.13.10")
  )

lazy val extractor = newProject("extractor")
  .settings(
    sbtPlugin := true,
    scalacOptions ++= Seq("-deprecation"),

    scalaVersion := scala210,

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.14" % Test withSources()
    ),
    // used only for testing, see publishVersions for versions that are actually used to publish artifacts
    crossSbtVersions := Nil, // handled by explicitly setting sbtVersion via scalaVersion
    crossScalaVersions := Seq(scala212, scala210),
    pluginCrossBuild / sbtVersion := {
      // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
      scalaBinaryVersion.value match {
        case "2.10" => "0.13.17"
        case "2.12" => "1.2.1"
      }
    }
  )
  .enablePlugins(TestDataDumper)

lazy val sbtStructure = project.in(file("."))
  .settings(
    // disable publishing in root project
    publish / skip := true,
    crossScalaVersions := Nil,
    crossSbtVersions := Nil,
    sonatypePublishTo := None
  )
  .aggregate(core, extractor)

Global / excludeLintKeys += Keys.crossSbtVersions

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
