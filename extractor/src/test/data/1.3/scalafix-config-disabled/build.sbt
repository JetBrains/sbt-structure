name := "scalafix-config-disabled"

scalaVersion := "2.13.18"

// sbt-scalafix plugin automatically sets ScalafixInternalRule / bspEnabled := false
// This tests that the ScalafixInternalRule configuration is correctly filtered out
