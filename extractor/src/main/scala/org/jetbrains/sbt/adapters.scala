package org.jetbrains.sbt

import sbt._

case class LoadedBuildUnitAdapter(delegate: Load.LoadedBuildUnit) {
  def imports: Seq[String] =
    delegate.imports

  def pluginsClasspath: Seq[Attributed[File]] =
    delegate.unit.plugins.pluginData.dependencyClasspath
}

case class UpdateReportAdapter(configurationToModule: Map[Configuration, Seq[ModuleReportAdapter]]) {
  def this(delegate: UpdateReport) {
    this(delegate.configurations.map { report =>
      (config(report.configuration), report.modules.map(new ModuleReportAdapter(_)))
    }.toMap)
  }

  def allModules: Seq[ModuleReportAdapter] =
    configurationToModule.values.toSeq.flatten

  def modulesFrom(configuration: Configuration): Seq[ModuleReportAdapter] =
    configurationToModule.getOrElse(configuration, Seq.empty)
}

case class ModuleReportAdapter(moduleId: ModuleID, artifacts: Seq[(Artifact, File)]) {
  def this(delegate: ModuleReport) {
    this(delegate.module, delegate.artifacts)
  }
}
