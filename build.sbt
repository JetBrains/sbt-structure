import xerial.sbt.Sonatype.GitHubHosting

ThisBuild / organization := "org.jetbrains.scala"
ThisBuild / sonatypeProfileName := "org.jetbrains"
ThisBuild / homepage := Some(url("https://github.com/JetBrains/sbt-structure"))
ThisBuild / sonatypeProjectHosting := Some(GitHubHosting("JetBrains", "sbt-structure", "scala-developers@jetbrains.com"))
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

lazy val sbtStructure = project.in(file("."))
  .aggregate(core, extractor)
  .settings(
    name := "sbt-structure",
    // disable publishing in root project
    publish / skip := true,
    crossScalaVersions := List.empty,
    crossSbtVersions := List.empty,
    sonatypePublishTo := None
  )

val scala210: String = "2.10.7"
val scala212: String = "2.12.17"

lazy val core = project.in(file("core"))
  .settings(
    name := "sbt-structure-core",
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala",
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaBinaryVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor >= 11 =>
          Seq("org.scala-lang.modules" %% "scala-xml" % "1.3.0")
        case _ =>
          Seq.empty
      }
    },
    crossScalaVersions := Seq("2.13.10", scala212, "2.11.12", scala210)
  )

lazy val extractor = project.in(file("extractor"))
  .settings(
    name := "sbt-structure-extractor",
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala",
    scalacOptions ++= Seq("-deprecation"),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.14" % Test,
    crossScalaVersions := Seq(scala212, scala210),
    pluginCrossBuild / sbtVersion := {
      // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
      scalaBinaryVersion.value match {
        case "2.10" => "0.13.17"
        case "2.12" => "1.2.1"
      }
    }
  )
  .enablePlugins(SbtPlugin, TestDataDumper)

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
