package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.AndroidSdkPluginExtractor.{androidTask, _}
import org.jetbrains.sbt.structure.{AndroidData, StructureData}
import sbt.{Def, Keys, Task}


/**
  * Created by jast on 2017-02-27.
  */
package object extractors {

  val extractStructure: Def.Initialize[Task[StructureData]] = Def.task {
    StructureData(
      Keys.sbtVersion.value,
      StructureKeys.extractProjects.value,
      StructureKeys.extractRepository.value,
      StructureKeys.localCachePath.value
    )
  }

  val extractAndroidSdkPlugin: Def.Initialize[Task[Option[AndroidData]]] = Def.taskDyn {
    val state = sbt.Keys.state.value
    val projectRef = sbt.Keys.thisProjectRef.value
    val androidTaskOpt = androidTask(state, projectRef)

    Def.task {
      androidTaskOpt.getOrElse(Option.empty[AndroidData].toTask).value
    }
  }
}
