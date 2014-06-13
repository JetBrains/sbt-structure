sbtPlugin := true

name := "sbt-structure"

organization := "org.jetbrains"

version := "2.4.0" // Semantic Versioning

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.2" % "test"

addSbtPlugin("com.hanhuy.sbt" % "android-sdk-plugin" % "1.2.16")
