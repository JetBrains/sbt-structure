package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.AndroidSdkPluginExtractor._
import org.jetbrains.sbt.structure.AndroidData
import sbt.Project.Initialize
import sbt.Task

/**
  * Created by jast on 2016-12-14.
  */
object tasks {

  def extractAndroidSdkPlugin: Initialize[Task[Option[AndroidData]]] =
    (sbt.Keys.state, sbt.Keys.thisProjectRef) flatMap { (state, projectRef) =>

      val androidTaskOpt = androidTask(state, projectRef)
      androidTaskOpt.getOrElse(None.toTask)
    }
}
