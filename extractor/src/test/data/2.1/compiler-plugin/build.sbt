name := "some-name"
organization := "some-organization"
version := "1.2.3"

val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.16",
    // Reproduces SCL-25761: under sbt 2 the `-Xplugin` jar path is virtualized as `${CSR_CACHE}/...`.
    // The extractor must resolve it to an absolute (machine) path so no placeholder leaks into the structure.
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
