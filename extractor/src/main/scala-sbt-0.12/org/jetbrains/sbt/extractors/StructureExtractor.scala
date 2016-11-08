package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.StructureKeys
import org.jetbrains.sbt.structure.StructureData
import sbt.Project.Initialize
import sbt._


object StructureExtractor {

  def taskDef: Initialize[Task[StructureData]] =
    ( StructureKeys.extractProjects
    , StructureKeys.extractRepository
    , Keys.sbtVersion) map {
      (projects, repository, sbtVersion) =>
        val localCachePath  = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
        StructureData(sbtVersion, projects, repository, localCachePath)
    }
}
