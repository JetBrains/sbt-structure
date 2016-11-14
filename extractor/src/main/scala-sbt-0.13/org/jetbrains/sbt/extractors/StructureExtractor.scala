package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.StructureKeys
import org.jetbrains.sbt.structure.StructureData
import sbt._

import scala.language.reflectiveCalls

object StructureExtractor {

  def taskDef: Def.Initialize[Task[StructureData]] = Def.task {
    val projects = StructureKeys.extractProjects.value
    val repository = StructureKeys.extractRepository.value
    val sbtVersion = Keys.sbtVersion.value

    val localCachePath  = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
    StructureData(sbtVersion, projects, repository, localCachePath)
  }
}
