package org.jetbrains.sbt

import sbt._
import sbt.Keys._
import sbt.Value
import sbt.Load.BuildStructure
import Android._

/**
 * @author Pavel Fatin
 */
trait ExtractorBase {
  def extractStructure(state: State, download: Boolean, resolveClassifiers: Boolean, resolveSbtClassifiers: Boolean): StructureData
}
