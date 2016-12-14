package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.AndroidSdkPluginExtractor._
import org.jetbrains.sbt.structure.AndroidData
import sbt.{Def, Task}

/**
  * Created by jast on 2016-12-14.
  */
object tasks {

  def extractAndroidSdkPlugin: Def.Initialize[Task[Option[AndroidData]]] = Def.taskDyn {

    val state = sbt.Keys.state.value
    val projectRef = sbt.Keys.thisProjectRef.value

    val androidTaskOpt = androidTask(state, projectRef)

    Def.task {
      androidTaskOpt.getOrElse(Option.empty[AndroidData].toTask).value
    }
  }
}
