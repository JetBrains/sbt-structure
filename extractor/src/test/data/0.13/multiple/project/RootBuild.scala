import sbt._
import Keys._

object RootBuild extends Build {
    lazy val root = Project(id = "root",
                            base = file("")) aggregate(foo) dependsOn(bar)

    lazy val foo = Project(id = "foo",
                           base = file("foo"))

    lazy val bar = Project(id = "bar",
                           base = file("bar"))
}