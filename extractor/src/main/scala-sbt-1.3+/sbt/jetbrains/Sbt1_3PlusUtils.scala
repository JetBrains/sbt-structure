package sbt.jetbrains

import lmcoursier.definitions.CacheLogger
import sbt.Keys.useSuperShell
import sbt.internal.CommandStrings.LastCommand
import sbt.{Command, Def, Keys, Logger, Task, ThisBuild}

trait Sbt1_3PlusUtils {
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

  val isFailedReload: Def.Initialize[Task[Boolean]] = Def.task {
    val state = sbt.Keys.state.value
    val out = new java.io.ByteArrayOutputStream()
    Console.withOut(out) {
      Command.process(LastCommand, state)
    }

    val ansiPattern = "\\u001B\\[[;\\d]*m".r
    val lastOutput = ansiPattern.replaceAllIn(out.toString, "")
    lastOutput.contains("Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?")
  }
}
