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
    crossScalaVersions := Seq("2.9.3", "2.10.6", "2.11.9", "2.12.2")
  )

lazy val extractor = newProject("extractor")
  .settings(
    name := name.value + "-" + (sbtVersion in pluginCrossBuild).value,

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

    crossSbtVersions := Seq("0.12.4", "0.13.0", "0.13.9", "0.13.13", "1.0.0-M5"),

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
      val sbtVer = (sbtVersion in sbtPlugin).value
      val srcs = (sources in Compile).value
      // remove the AutoPlugin since it doesn't compile when testing for sbt 0.13.0
      if (sbtVer == "0.13.0")
        srcs.filterNot(_.getName == "StructurePlugin.scala")
      else srcs
    }
  )
  .enablePlugins(TestDataDumper)

lazy val sbtStructure = project.in(file(".")).aggregate(core, extractor)

lazy val testSetup = taskKey[Unit]("Setup tests for extractor")

