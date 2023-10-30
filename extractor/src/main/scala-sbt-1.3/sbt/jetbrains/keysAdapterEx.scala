package sbt.jetbrains

import sbt.{Def, File, Task, TaskKey}

object keysAdapterEx {
  //NOTE: sbt.Keys.scalaCompilerBridgeBinaryJar exists since SBT 1.2.3, so we detect it only since 1.3.0
  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    sbt.Keys.scalaCompilerBridgeBinaryJar
  }
}
