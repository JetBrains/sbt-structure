package org.jetbrains.sbt

import sbt.*
import sbt.internal.LoadedBuildUnit
import sbt.jetbrains.PluginCompat.*

import scala.collection.Seq

case class LoadedBuildUnitAdapter(delegate: LoadedBuildUnit) {

  def uri: URI =
    delegate.unit.uri

  def imports: Seq[String] =
    delegate.imports.toImmutableSeq

  def pluginsClasspath: Seq[Attributed[File]] =
    toAttributedFiles(delegate.unit.plugins.pluginData.dependencyClasspath).toImmutableSeq
}

case class UpdateReportAdapter(configurationToModule: Map[String, Seq[ModuleReportAdapter]]) {
  def this(delegate: UpdateReport) = {
    this(delegate.configurations.map { report =>
      (report.configuration.name, report.modules.map(new ModuleReportAdapter(_)))
    }.toMap)
  }

  def allModules: Seq[ModuleReportAdapter] =
    configurationToModule.values.toSeq.flatten.toImmutableSeq

  def modulesFrom(configuration: String): Seq[ModuleReportAdapter] =
    configurationToModule.getOrElse(configuration, Seq.empty)
}

case class ModuleReportAdapter(moduleId: ModuleID, artifacts: Seq[(Artifact, File)]) {
  def this(delegate: ModuleReport) = {
    this(delegate.module, delegate.artifacts)
  }
}
