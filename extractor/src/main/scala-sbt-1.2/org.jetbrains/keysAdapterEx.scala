package sbt.jetbrains

import sbt.{Def, File, Task, Setting, TaskKey}

object keysAdapterEx {
  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    Def.task {
      None
    }
  }

  val artifactDownload: Seq[Setting[_]] = Seq.empty
}
