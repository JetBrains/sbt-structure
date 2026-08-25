lazy val kotlincOptions = SettingKey[Seq[String]]("kotlinc-options")

name := "kotlinc-options"
organization := "org.example"
version := "0.1.0"
scalaVersion := "2.13.16"

Compile / kotlincOptions := Seq("-Xjsr305=strict", "-language-version", "2.1")
Test / kotlincOptions := Seq("-Xcontext-receivers")
