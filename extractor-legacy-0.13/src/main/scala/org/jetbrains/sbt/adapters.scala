package org.jetbrains.sbt

import sbt._

case class LoadedBuildUnitAdapter(delegate: LoadedBuildUnit) {

  def uri: URI =
    delegate.unit.uri

  def imports: Seq[String] =
    delegate.imports

  def pluginsClasspath: Seq[Attributed[File]] =
    delegate.unit.plugins.pluginData.dependencyClasspath
}

case class UpdateReportAdapter(configurationToModule: Map[String, Seq[ModuleReportAdapter]]) {
  def this(delegate: UpdateReport) = {
    this(delegate.configurations.map { report =>
      (report.configuration, report.modules.map(new ModuleReportAdapter(_)))
    }.toMap)
  }

  def allModules: Seq[ModuleReportAdapter] =
    configurationToModule.values.toSeq.flatten

  def modulesFrom(configuration: String): Seq[ModuleReportAdapter] =
    configurationToModule.getOrElse(configuration, Seq.empty)
}

case class ModuleReportAdapter(moduleId: ModuleID, artifacts: Seq[(Artifact, File)]) {
  def this(delegate: ModuleReport) = {
    this(delegate.module, delegate.artifacts)
  }
}
