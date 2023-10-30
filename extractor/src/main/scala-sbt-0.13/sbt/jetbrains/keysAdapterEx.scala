package sbt.jetbrains

import sbt.{Def, File, Task, TaskKey}

object keysAdapterEx {
  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    Def.task {
      None
    }
  }
}
