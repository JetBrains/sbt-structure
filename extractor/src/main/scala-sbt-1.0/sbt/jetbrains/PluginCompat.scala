package sbt.jetbrains

import sbt.{Def, Setting, Task}

import java.io.File

object PluginCompat extends PluginCompatCommonSbt1
  with SlashSyntax {

  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    Def.task {
      None
    }
  }

  val artifactDownloadLoggerSettings: Seq[Setting[_]] = Seq.empty
}