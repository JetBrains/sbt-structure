package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.StructureData
import sbt._
import Project.Initialize

//import scala.language.reflectiveCalls

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
