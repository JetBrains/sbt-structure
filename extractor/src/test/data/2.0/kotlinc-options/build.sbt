lazy val kotlincOptions = SettingKey[Seq[String]]("kotlinc-options")

organization := "org.example"
version := "0.1.0"
scalaVersion := "3.6.2"

lazy val root = rootProject
  .settings(
    name := "kotlinc-options",
    Compile / kotlincOptions := Seq("-Xjsr305=strict", "-language-version", "2.1"),
    Test / kotlincOptions := Seq("-Xcontext-receivers")
  )
