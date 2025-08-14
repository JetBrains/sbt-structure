package sbt.jetbrains

import lmcoursier.definitions.CacheLogger
import sbt.Keys.useSuperShell
import sbt.{Def, Keys, Logger, Task, ThisBuild}

trait CoursierLoggerTaskDefinition {
  lazy val artifactDownloadCsrLogger: Def.Initialize[Task[Option[CacheLogger]]] = Def.task {
    val st = Keys.streams.value
    val progress = (ThisBuild / useSuperShell).value
    if (progress) None
    else Some(new MyCoursierLogger(st.log))
  }

  private class MyCoursierLogger(logger: Logger) extends CacheLogger {
    override def downloadedArtifact(url: String, success: Boolean): Unit = {
      if (success)
        logger.info(s"downloaded $url")
      else
        logger.info(s"downloading failed $url")
    }

    override def downloadingArtifact(url: String): Unit = {
      logger.info(s"downloading $url")
    }
  }
}
