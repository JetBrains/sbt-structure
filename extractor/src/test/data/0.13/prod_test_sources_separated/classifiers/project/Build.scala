import sbt._
import Keys._

object Test extends Build {
  lazy val foo = project in file("foo")
  lazy val bar = project in file("bar")

  val base = "org.apache.james" % "apache-mailet-base" % "2.5.0"
  val baseTest = "org.apache.james" % "apache-mailet-base" % "2.5.0" % "test" classifier "tests" classifier ""
}
