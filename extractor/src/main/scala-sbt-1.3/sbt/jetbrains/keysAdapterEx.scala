package sbt.jetbrains

import lmcoursier.definitions.CacheLogger
import sbt.KeyRanks.{DTask, Invisible}
import sbt.{Def, File, Global, GlobalScope, Keys, Logger, Setting, Task, TaskKey}

object keysAdapterEx {
  //NOTE: sbt.Keys.scalaCompilerBridgeBinaryJar exists since SBT 1.2.3, so we detect it only since 1.3.0
  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    sbt.Keys.scalaCompilerBridgeBinaryJar
  }
  val artifactDownloadTask: TaskKey[Option[CacheLogger]] = TaskKey("artifactDownloadTask", rank = DTask)
  lazy val artifactDownloadCsrLogger: Def.Initialize[Task[Option[CacheLogger]]] = Def.task {
    val st = Keys.streams.value
    Some(new CoursierLogger(st.log))

  }

  val artifactDownload: Seq[Setting[_]] = Seq(
    artifactDownloadTask := keysAdapterEx.artifactDownloadCsrLogger.value,
    Keys.csrLogger := keysAdapterEx.artifactDownloadCsrLogger.value
  )

  class CoursierLogger(logger: Logger) extends CacheLogger {
    override def downloadedArtifact(url: String, success: Boolean): Unit = {
      logger.info(s"downloaded $url")
    }

    override def downloadingArtifact(url: String): Unit = {
      logger.info(s"downloading $url")
    }
  }
}
