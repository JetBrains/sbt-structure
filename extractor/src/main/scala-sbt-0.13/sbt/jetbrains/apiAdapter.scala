package sbt.jetbrains

import sbt.{ConfigurationReport, GetClassifiersModule, Global, Reference, Scope, Select}
import sbt.jetbrains.apiAdapter._

/**
  * Hacky workaround for some types having been moved around in sbt 1.0
  *
  * This one is just a dummy and overwrites no imports
  */
object apiAdapter {
  implicit class GetClassifiersModuleAdapter(module: GetClassifiersModule) {
    def withClassifiers(classifiers: Vector[String]): GetClassifiersModule =
      module.copy(classifiers = classifiers)
  }

  // copied from sbt.internal.Load
  def projectScope(project: Reference): Scope = Scope(Select(project), Global, Global, Global)

  def configReportName(configReport: ConfigurationReport): String = configReport.configuration
}
