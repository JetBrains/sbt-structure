package sbt.jetbrains

import sbt.{Def, Setting, Task}

import java.io.File

object PluginCompat extends PluginCompatCommonSbt1
  with SlashSyntax {

  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] =
    Def.task(None)

  val isFailedReload: Def.Initialize[Task[Boolean]] = Def.task { false }

  val artifactDownloadLoggerSettings: Seq[Setting[_]] = Seq.empty
}
