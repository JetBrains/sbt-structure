package sbt.jetbrains

import sbt.{Def, File, Task}

/**
 * The class contains wrapper definitions for keys/settings that exist only since sbt 1.3
 */
trait PluginCompat_SinceSbt_1_3 extends CoursierLoggerSettingsCompat {
  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    sbt.Keys.scalaCompilerBridgeBinaryJar
  }
}
