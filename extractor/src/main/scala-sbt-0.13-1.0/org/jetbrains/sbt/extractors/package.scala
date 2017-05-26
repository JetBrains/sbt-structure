package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.AndroidSdkPluginExtractor.androidTask
import org.jetbrains.sbt.structure.{AndroidData, ProjectData, RepositoryData, StructureData}
import sbt.{Def, Keys, Task}
import org.jetbrains.sbt.extractors.AndroidSdkPluginExtractor._


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

  /** Extract structure with parameterized options. Useful when called from an inputTask. */
  def extractStructure(options: Options): Def.Initialize[Task[StructureData]] = Def.task {
    StructureData(
      Keys.sbtVersion.value,
      StructureKeys.extractProjects.value,
      RepositoryExtractor.taskDef(options).value,
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
