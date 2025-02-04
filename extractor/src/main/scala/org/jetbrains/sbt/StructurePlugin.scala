package org.jetbrains.sbt

import sbt.*
import sbt.jetbrains.PluginCompat.*
import sbt.plugins.JvmPlugin

//The class is used indirectly by Scala Plugin (see also SCL-20353)
//noinspection ScalaUnusedSymbol
object StructurePlugin extends AutoPlugin {

  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override lazy val globalSettings: Seq[Setting[?]] = Seq(
    StructureKeys.sbtStructureOutputFile := None,
    StructureKeys.sbtStructureOptions := "prettyPrint download",
    StructureKeys.dumpStructureTo := PluginOnlyTasksCompat.dumpStructureTo.evaluated
  ) ++ CreateTasks.globalSettings

  override lazy val projectSettings: Seq[Setting[?]] = CreateTasks.projectSettings.toSbtSeqType
}
