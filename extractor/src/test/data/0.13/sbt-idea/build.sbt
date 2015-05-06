name := "some-name"

organization := "some-organization"

version := "1.2.3"

javaHome := Some(new File("some/home"))

javacOptions := Seq("-j1", "-j2")

scalaVersion := "2.10.1"

scalacOptions := Seq("-s1", "-s2")

ideaExcludeFolders := Seq(".idea")

ideaBasePackage := Some("org.jetbrains")

lazy val projectToSkip = project.in(file("skip")).settings(ideaIgnoreModule := true)