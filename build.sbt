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
val scala212_Earlier: String = "2.12.19" //used for sbt < 1.3
val scala212: String = "2.12.20" //used for sbt >= 1.3
val scala3: String = "3.6.2" //used for sbt 2

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
    crossScalaVersions := Seq("2.13.16", scala212, "2.11.12"),
    sonatypeSettings
  )

val SbtVersion_1_2 = "1.2.1"
val SbtVersion_1_3 = "1.3.0"
val SbtVersion_2 = "2.0.0-M3"

lazy val extractor = project.in(file("extractor"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-structure-extractor",
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala",
    scalacOptions ++= Seq("-deprecation", "-feature") ++ {
      // Mute some warnings
      // We have to use some deprecated things because we cross-compile for 2.10, 2.12 and 3.x
      if (scalaBinaryVersion.value.startsWith("3")) {
        val patterns = Seq(
          """msg=(?s)`_` is deprecated for wildcard arguments of types. use `\?` instead.*:silent""",
          """msg=(?s)method mapValues in trait MapOps is deprecated since 2.13.0.*:silent""",
          """msg=.*is no longer supported for vararg splices.*:silent""",
          """msg=method toIterable in class IterableOnceExtensionMethods is deprecated since 2.13.0.*:silent""",
          """msg=method right in class Either is deprecated since 2.13.0.*:silent""",
          """msg=method get in class RightProjection is deprecated since 2.13.0.*:silent""",
          """msg=object JavaConverters in package scala.collection is deprecated since 2.13.0.*:silent""",
          // We have to use IntegrationTest to support older sbt versions
          """msg=value IntegrationTest in trait LibraryManagementSyntax is deprecated since 1.9.0.*:silent""",
        )
        Seq(s"-Wconf:${patterns.mkString(",")}")
      } else
        Seq.empty
    },
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.dom4j" % "dom4j" % "2.1.4" % Test
    ),
//    scalaVersion := scala212,
    scalaVersion := scala3,
    crossScalaVersions := Seq(
      scala212_Earlier,
      scala212,
      scala3,
    ),
    crossSbtVersions := Seq(
      SbtVersion_1_2,
      SbtVersion_1_3,
      SbtVersion_2
    ),
    pluginCrossBuild / sbtVersion := {
      // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
      val scalaVer = scalaVersion.value
      if (scalaVer == scala212_Earlier)
        SbtVersion_1_2
      else if (scalaVer == scala212)
        SbtVersion_1_3
      else if (scalaVer == scala3)
        SbtVersion_2
      else
        throw new AssertionError(s"Unexpected scala version $scalaVer")
    },
    // By default, when you crosscompile sbt plugin for multiple sbt 1.x versions,
    // it will use the same binary version 1.0 for all of them
    // It will use the same source directory `scala-sbt-1.0`, same target dirs and same artifact names.
    // But we need different directories because some code compiles in sbt 1.x but not in sbt 1.y
    pluginCrossBuild / sbtBinaryVersion := {
      val sbtVersion3Digits = (pluginCrossBuild / sbtVersion).value
      val sbtVersion2Digits = sbtVersion3Digits.substring(0, sbtVersion3Digits.lastIndexOf("."))
      sbtVersion2Digits
    },
    Compile / unmanagedSourceDirectories ++= {
      val sbtBinVer = (pluginCrossBuild / sbtBinaryVersion).value
      val baseDir = (Compile / sourceDirectory).value
      //shared source dir for all sbt 1.x
      val shared1 = if (sbtBinVer.startsWith("1")) Seq(baseDir / "scala-sbt-1.x") else Nil
      //shared source dir for all sbt 1.x and sbt 2.x
      val shared1and2 = Seq(baseDir / "scala-sbt-1&2")
      shared1  ++ shared1and2
    },
    // Only run tests in the latest Scala 2
    // TODO: ensure CI is updated (TeamCity & GitHub)
    Test / unmanagedSourceDirectories := {
      if (scalaVersion.value == scala212)
        (Test / unmanagedSourceDirectories).value
      else
        Nil
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
