import lmcoursier.internal.shaded.coursier.core.Version
import sbt.{Def, ThisBuild, url}

import scala.collection.{Seq, mutable}

lazy val PublishingSettings: Seq[Def.Setting[?]] = Seq(
  organization := "org.jetbrains.scala",

  // Optional but nice-to-have
  organizationName := "JetBrains",
  organizationHomepage := Some(url("https://www.jetbrains.com/")),

  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),

  homepage := Some(url("https://github.com/JetBrains/sbt-structure")),

  // Source-control coordinates
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/JetBrains/sbt-structure"),
      "git@github.com:JetBrains/sbt-structure.git"
    )
  ),

  // Required by Sonatype for publishing
  developers := List(
    Developer(
      id = "JetBrains",
      name = "JetBrains",
      email = "scala-developers@jetbrains.com",
      url = url("https://github.com/JetBrains")
    )
  ),
)

lazy val sbtStructure = project.in(file("."))
  .aggregate(core, extractor, extractorLegacy_013)
  .settings(PublishingSettings)
  .settings(
    name := "sbt-structure",
    // disable publishing in the root project
    publish / skip := true,
    crossScalaVersions := List.empty,
    crossSbtVersions := List.empty,
    publishTo := None,
  )

//NOTE: an extra scala 2.12 version is used just to distinguish between different sbt 1.x versions
// when calculating pluginCrossBuild / sbtVersion
val scala212_Earlier: String = "2.12.19" //used for sbt < 1.3
val scala212: String = "2.12.20" //used for sbt >= 1.3
val scala3: String = "3.7.3" //used for sbt 2
val Scala_2_10_Legacy = "2.10.7"

val SbtVersion_1_0 = "1.0.0"
val SbtVersion_1_3 = "1.3.0"
val SbtVersion_2 = "2.0.0-RC5" //TODO: update to the latest?
val SbtVersion_0_13_Legacy = "0.13.17"

val CommonSharedCoreDataSourcesSettings: Seq[Def.Setting[Seq[File]]] = Seq(
  Compile / unmanagedSourceDirectories +=
    (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala",
)

lazy val core = project.in(file("core"))
  .settings(PublishingSettings)
  .settings(
    name := "sbt-structure-core",
    libraryDependencies ++= {
      val scalaVersion = Version(scalaBinaryVersion.value)
      if (scalaVersion >= Version("2.12"))
        Seq("org.scala-lang.modules" %% "scala-xml" % "2.3.0")
      else
        Nil
    },
    crossScalaVersions := Seq("2.13.16", scala212),
    CommonSharedCoreDataSourcesSettings,
  )

lazy val extractor = project.in(file("extractor"))
  .enablePlugins(SbtPlugin)
  .settings(PublishingSettings)
  .settings(
    name := "sbt-structure-extractor",
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
    scalaVersion := scala212,
    //    scalaVersion := scala3,
    crossScalaVersions := Seq(
      scala212_Earlier,
      scala212,
      scala3,
    ),
    crossSbtVersions := Seq(
      SbtVersion_1_0,
      SbtVersion_1_3,
      SbtVersion_2
    ),
    pluginCrossBuild / sbtVersion := {
      // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
      val scalaVer = scalaVersion.value
      scalaVer match {
        case `scala212_Earlier` => SbtVersion_1_0
        case `scala212` => SbtVersion_1_3
        case `scala3` => SbtVersion_2
        case _ =>
          throw new AssertionError(s"Unexpected scala version $scalaVer")
      }
    },
    // By default, when you crosscompile sbt plugin for multiple sbt 1.x versions,
    // it will use the same binary version 1.0 for all of them
    // It will use the same source directory `scala-sbt-1.0`, same target dirs and same artifact names.
    // But we need different directories because some code compiles in sbt 1.x but not in sbt 1.y
    pluginCrossBuild / sbtBinaryVersion := {
      val originalBinaryVersion = (pluginCrossBuild / sbtBinaryVersion).value
      val sbtVersion3Digits = (pluginCrossBuild / sbtVersion).value
      sbtVersion3Digits match {
        case SbtVersion_2 =>
          // For sbt 2, the binary version has been changed to a simple "2".
          // https://github.com/sbt/librarymanagement/pull/437
          originalBinaryVersion
        case SbtVersion_1_3 =>
          // For sbt [1.3.x, 2.x) we set our own custom binary version.
          // This is because we offer the sbt-structure-extractor as a specially compiled binary for sbt 1.3.x and above.
          "1.3"
        case SbtVersion_1_0 =>
          // For sbt [1.0.x, 1.2.x], we use the default sbt 1 binary version "1.0".
          originalBinaryVersion
      }
    },
    Compile / unmanagedSourceDirectories ++= {
      val sbtVersion = Version((pluginCrossBuild / Keys.sbtVersion).value)
      val baseDir = (Compile / sourceDirectory).value

      val result = mutable.Buffer[File]()
      if (sbtVersion.repr.startsWith("1")) {
        result += baseDir / "scala-sbt-1.0-1.x"

        if (sbtVersion >= Version("1.3")) {
          result += baseDir / "scala-sbt-1.3-1.x"
        }
      }
      if (sbtVersion >= Version("1.3"))
        result += baseDir / "scala-sbt-1.3+"

      result
    },
    CommonSharedCoreDataSourcesSettings,
    // We only run tests in Scala 2.
    // This is done to avoid cross-compilation for test sources, which would introduce some redundant burden.
    // TODO: ensure CI is updated (TeamCity & GitHub)
    Test / unmanagedSourceDirectories := {
      if (scalaVersion.value.startsWith("2"))
        (Test / unmanagedSourceDirectories).value
      else
        Nil
    },
    Test / parallelExecution := false
  )

// We use separate module for 0.13 with many sources duplicated as an alternative to cross-compilation.
// Such an approach should be easier than cross-compiling against 0.13, 1.0, 1.2, 2.x.
// Trying to cross-compile between 3 major versions of sbt (and thus scala 2.10, 2.12, 3.x) is very fragile
lazy val extractorLegacy_013 = project.in(file("extractor-legacy-0.13"))
  .enablePlugins(SbtPlugin)
  .settings(PublishingSettings)
  .settings(
    name := "sbt-structure-extractor-legacy-0.13",
    // NOTE: use the same module name for 0.13 when publishing.
    // We have to do this explicitly because we extracted the 0.13 code to a separate project with a different name
    // which is used as the module name by default.
    moduleName := (extractor / Keys.moduleName).value,

    scalaVersion := Scala_2_10_Legacy,
    crossScalaVersions := Seq(Scala_2_10_Legacy),
    crossSbtVersions := Seq(SbtVersion_0_13_Legacy),
    pluginCrossBuild / sbtVersion := SbtVersion_0_13_Legacy,
    CommonSharedCoreDataSourcesSettings,
  )

// just running "ci-release" in the root will run it for all aggregated projects
// Running extra "clean" to ensure that there is no unexpected files cached
// in sonatype-staging or sonatype-bundle local directories in target directory
val publishAll =
  "; clean ; ci-release"
val publishCoreCommand =
  "; clean ; project core ; ci-release"
val publishExtractorCommand =
  "; clean ; project extractor ; ci-release ; project extractorLegacy_013 ; ci-release"

addCommandAlias("publishAll", publishAll)
addCommandAlias("publishCore", publishCoreCommand)
addCommandAlias("publishExtractor", publishExtractorCommand)

// note: we can only run tests for Scala 2 (see comments in extractor module)
addCommandAlias("crossCompileAndRunTests", s"""; +compile ; project extractor ; set scalaVersion := "$scala212" ; test""")
