import xerial.sbt.Sonatype.GitHubHosting

ThisBuild / organization := "org.jetbrains.scala"
ThisBuild / homepage := Some(url("https://github.com/JetBrains/sbt-structure"))
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

lazy val sonatypeSettings = Seq(
  sonatypeProfileName := "org.jetbrains",
  sonatypeProjectHosting := Some(GitHubHosting("JetBrains", "sbt-structure", "scala-developers@jetbrains.com"))
)

lazy val sbtStructure = project.in(file("."))
  .aggregate(core, extractor)
  .settings(
    name := "sbt-structure",
    // disable publishing in root project
    publish / skip := true,
    crossScalaVersions := List.empty,
    crossSbtVersions := List.empty,
    sonatypePublishTo := None,
    sonatypeSettings
  )

val scala210: String = "2.10.7"
//NOTE: extra scala 2.12 version is used just to distinguish between different sbt 1.x versions
// when calculating pluginCrossBuild / sbtVersion
val scala212_6: String = "2.12.6" //used for sbt < 1.3
val scala212: String = "2.12.20" //used for sbt >= 1.3

lazy val core = project.in(file("core"))
  .settings(
    name := "sbt-structure-core",
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala",
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaBinaryVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor >= 12 =>
          Seq("org.scala-lang.modules" %% "scala-xml" % "2.3.0")
        case Some((2, 11)) =>
          Seq("org.scala-lang.modules" %% "scala-xml" % "1.3.1")
        case _ =>
          Seq.empty
      }
    },
    crossScalaVersions := Seq("2.13.16", scala212, "2.11.12", scala210),
    sonatypeSettings
  )

val SbtVersion_0_13 = "0.13.17"
val SbtVersion_1_2 = "1.2.1"
val SbtVersion_1_3 = "1.3.0"

lazy val extractor = project.in(file("extractor"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-structure-extractor",
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala",
    scalacOptions ++= Seq("-deprecation"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.dom4j" % "dom4j" % "2.1.4" % Test
    ),
    scalaVersion := scala212,
    crossScalaVersions := Seq(
      scala212,
      scala212_6,
      scala210
    ),
    crossSbtVersions := Seq(
      SbtVersion_0_13,
      SbtVersion_1_2,
      SbtVersion_1_3,
    ),
    pluginCrossBuild / sbtVersion := {
      // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
      val scalaVer = scalaVersion.value
      if (scalaBinaryVersion.value == "2.10")
        SbtVersion_0_13
      else if (scalaVer == scala212_6)
        SbtVersion_1_2
      else if (scalaVer == scala212)
        SbtVersion_1_3
      else
        throw new AssertionError(s"Unexpected scala version $scalaVer")
    },
    //By default when you crosscompile sbt plugin for multiple sbt 1.x versions it will use same binary version 1.0 for all of them
    //It will use the same source directory `scala-sbt-1.0`, same target dirs, same artifact names
    //But we need different directories because some code compiles in sbt 1.x but not in sbt 1.y
    pluginCrossBuild / sbtBinaryVersion := {
      val sbtVersion3Digits = (pluginCrossBuild / sbtVersion).value
      val sbtVersion2Digits = sbtVersion3Digits.substring(0, sbtVersion3Digits.lastIndexOf("."))
      sbtVersion2Digits
    },
    Compile / unmanagedSourceDirectories ++= {
      val sbtBinVer = (pluginCrossBuild / sbtBinaryVersion).value
      if (sbtBinVer.startsWith("0")) Nil else {
        val baseDir = (Compile / sourceDirectory).value
        Seq(baseDir / "scala-sbt-1.x") //shared source dir for all sbt 1.x
      }
    },
    sonatypeSettings
  )

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
