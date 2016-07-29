import android.Keys._
import android.Dependencies.aar

android.Plugin.androidBuild

platformTarget in Android := "android-22"

libraryDependencies ++= Seq(
  aar("com.android.support" % "support-v4" % "20.0.0"),
//  apklib("com.actionbarsherlock" % "actionbarsherlock" % "4.4.0"),
  "com.hanhuy.android" %% "scala-common" % "1.3",
  "ch.acra" % "acra" % "4.8.2",
  "com.lihaoyi" %% "scalarx" % "0.3.0"

)

scalaVersion := "2.11.8"

proguardConfig in Android := Seq("test option")

