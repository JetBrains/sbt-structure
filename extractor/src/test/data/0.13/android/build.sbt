import android.Keys._
import android.Dependencies.aar

android.Plugin.androidBuild

platformTarget in Android := "android-22"

libraryDependencies ++= Seq(
  aar("com.android.support" % "support-v4" % "20.0.0"),
  apklib("com.actionbarsherlock" % "actionbarsherlock" % "4.4.0")
)

scalaVersion := "2.11.1"

proguardConfig in Android := Seq("test option")

