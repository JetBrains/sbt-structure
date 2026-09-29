scalaVersion := "2.13.16"

managedScalaInstance := false

ivyConfigurations += Configurations.ScalaTool

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.13.18",
  "org.scala-lang" % "scala-compiler" % "2.13.18" % "scala-tool"
)
