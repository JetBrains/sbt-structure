
import sbt._
import _root_.play.PlayScala

object MyBuild extends Build {

  lazy val root = (project in file("")).enablePlugins(PlayScala)

}
