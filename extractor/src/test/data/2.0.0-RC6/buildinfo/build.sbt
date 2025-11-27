lazy val buildinfo = project.in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "buildinfo",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "org.example"
  )
