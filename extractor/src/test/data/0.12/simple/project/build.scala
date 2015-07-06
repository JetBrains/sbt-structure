import sbt._
import Keys._


object Build extends Build {
  lazy val root = Project(id="simple", base=file("."))
}
