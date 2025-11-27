package sbt.jetbrains

import sbt.{Def, File, Task}

object PluginCompat extends PluginCompatCommonSbt1 with CoursierLoggerSettingsCompat {
  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] =
    sbt.Keys.scalaCompilerBridgeBinaryJar.toTask
}
