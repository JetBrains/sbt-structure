package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.AndroidSdkPluginExtractor.androidTask
import org.jetbrains.sbt.structure.{AndroidData, StructureData}
import sbt.{Def, Keys, Task}
import org.jetbrains.sbt.extractors.AndroidSdkPluginExtractor._


/**
  * Created by jast on 2017-02-27.
  */
package object extractors {

  def extractStructure: Def.Initialize[Task[StructureData]] =
    Def.taskDyn(extractStructure(StructureKeys.sbtStructureOpts.value))

  def extractStructure(options: Options): Def.Initialize[Task[StructureData]] = Def.task {
    val projects = UtilityTasks.extractProjects.value
    val repository = RepositoryExtractor.taskDef(options).value
    val sbtVersion = Keys.sbtVersion.value

    val localCachePath = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
    StructureData(sbtVersion, projects, repository, localCachePath)
  }

  def extractAndroidSdkPlugin: Def.Initialize[Task[Option[AndroidData]]] = Def.taskDyn {
    val state = sbt.Keys.state.value
    val projectRef = sbt.Keys.thisProjectRef.value
    val androidTaskOpt = androidTask(state, projectRef)

    Def.task {
      androidTaskOpt.getOrElse(Option.empty[AndroidData].toTask).value
    }
  }
}
