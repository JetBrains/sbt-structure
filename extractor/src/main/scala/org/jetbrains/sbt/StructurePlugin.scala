package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.UtilityTasks.writeToFile
import org.jetbrains.sbt.structure.XmlSerializer._
import sbt._
import sbt.complete.DefaultParsers
import sbt.plugins.JvmPlugin
import sbt.jetbrains.keysAdapterEx
import sbt.jetbrains.PluginCompat._

object StructurePlugin extends AutoPlugin {

  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    StructureKeys.sbtStructureOutputFile := None,
    StructureKeys.sbtStructureOptions := "prettyPrint download",
    StructureKeys.dumpStructureTo := pluginOnlyTasks.dumpStructureTo.evaluated
  ) ++ keysAdapterEx.artifactDownload ++ CreateTasks.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = CreateTasks.projectSettings.toSbtSeqType

}
