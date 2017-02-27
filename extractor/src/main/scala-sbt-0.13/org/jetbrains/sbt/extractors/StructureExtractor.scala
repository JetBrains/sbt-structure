package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.{Options, StructureKeys, UtilityTasks}
import org.jetbrains.sbt.structure.StructureData
import sbt._

import scala.language.reflectiveCalls

object StructureExtractor {

  def taskDef: Def.Initialize[Task[StructureData]] = Def.task {
    val projects = UtilityTasks.extractProjects.value
    val repository = RepositoryExtractor.taskDef.value
    val sbtVersion = Keys.sbtVersion.value

    val localCachePath = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
    StructureData(sbtVersion, projects, repository, localCachePath)
  }

  def taskDef(options: Options): Def.Initialize[Task[StructureData]] = Def.task {
    val projects = UtilityTasks.extractProjects.value
    val repository = RepositoryExtractor.taskDef(options).value
    val sbtVersion = Keys.sbtVersion.value

    val localCachePath = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
    StructureData(sbtVersion, projects, repository, localCachePath)
  }
}
