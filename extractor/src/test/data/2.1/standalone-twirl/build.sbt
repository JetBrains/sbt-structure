ThisBuild / scalaVersion := "2.13.18"

lazy val root = project.in(file(".")).enablePlugins(SbtTwirl).settings(
  TwirlKeys.templateImports += "support.Shared._",
  Compile / TwirlKeys.templateImports += "support.CompileOnly._",
  Test / TwirlKeys.templateImports += "support.TestOnly._"
)

lazy val plain = project.in(file("plain"))

lazy val emptyImports = project.in(file("emptyImports")).enablePlugins(SbtTwirl).settings(
  Compile / TwirlKeys.templateImports := Seq.empty
)

lazy val defaultsOnly = project.in(file("defaultsOnly")).enablePlugins(SbtTwirl)
