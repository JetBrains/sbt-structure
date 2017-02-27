package org.jetbrains.sbt

import sbt._
import sbt.plugins.JvmPlugin

/**
  * Created by jast on 2017-02-22.
  */
object StructurePlugin extends AutoPlugin {

  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    StructureKeys.sbtStructureOutputFile := None, // TODO deprecate for plugin use
    StructureKeys.sbtStructureOptions := "prettyPrint download" // TODO deprecate for plugin use
  ) ++ CreateTasks.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = CreateTasks.projectSettings

}
