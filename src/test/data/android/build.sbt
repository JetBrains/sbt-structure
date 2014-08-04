import android.Keys._
import android.Dependencies._

android.Plugin.androidBuild

platformTarget in Android := "android-18"

name := "name"

organization := "com.example"

version := "1.2.3"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  aar("org.apmem.tools" % "layouts" % "1.0")
)

proguardScala in Android := true

proguardOptions in Android ++= Seq(
  "-ignorewarnings",
  "-keep class scala.Dynamic"
)
