scalaVersion in ThisBuild := "2.13.18"

lazy val root = project.in(file(".")).enablePlugins(SbtTwirl).settings(
  TwirlKeys.templateImports += "support.Shared._",
  TwirlKeys.templateImports in Compile += "support.CompileOnly._",
  TwirlKeys.templateImports in Test += "support.TestOnly._"
)

lazy val plain = project.in(file("plain"))

lazy val emptyImports = project.in(file("emptyImports")).enablePlugins(SbtTwirl).settings(
  TwirlKeys.templateImports in Compile := Seq.empty
)

lazy val defaultsOnly = project.in(file("defaultsOnly")).enablePlugins(SbtTwirl)
