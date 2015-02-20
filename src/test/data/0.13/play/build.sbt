name := "my-first-app"

version := "1.0.0-SNAPSHOT"

lazy val root = (project in file("")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
    jdbc,
    anorm
   )

