package sbt.jetbrains

import lmcoursier.definitions.CacheLogger
import sbt.Keys.useSuperShell
import sbt.{Def, File, Keys, Logger, Setting, Task, ThisBuild}

/**
 * The class contains wrapper definitions for keys/settings that exist only since sbt 1.3
 */
trait PluginCompat_SinceSbt_1_3 {
  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    sbt.Keys.scalaCompilerBridgeBinaryJar
  }

  lazy val artifactDownloadLoggerSettings: Seq[Setting[_]] = Seq(
    Keys.csrLogger := artifactDownloadCsrLogger.value
  )

  private lazy val artifactDownloadCsrLogger: Def.Initialize[Task[Option[CacheLogger]]] = Def.task {
    val st = Keys.streams.value
    val progress = (ThisBuild / useSuperShell).value
    if (progress) None
    else Some(new MyCoursierLogger(st.log))
  }

  private class MyCoursierLogger(logger: Logger) extends CacheLogger {
    override def downloadedArtifact(url: String, success: Boolean): Unit = {
      logger.info(s"downloaded $url")
    }

    override def downloadingArtifact(url: String): Unit = {
      logger.info(s"downloading $url")
    }
  }
}
