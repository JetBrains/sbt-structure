package org.jetbrains.sbt

import sbt.*
import sbt.jetbrains.PluginCompat.*
import sbt.plugins.JvmPlugin

import scala.util.Try

//The class is used indirectly by Scala Plugin (see also SCL-20353)
//noinspection ScalaUnusedSymbol
object StructurePlugin extends AutoPlugin {

  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override lazy val globalSettings: Seq[Setting[?]] = Seq(
    StructureKeys.sbtStructureOutputFile := retrieveOutputFile.orElse(None),
    StructureKeys.sbtStructureOptions := retrieveOptions.getOrElse("prettyPrint download"),
    StructureKeys.dumpStructureTo := PluginOnlyTasksCompat.dumpStructureTo.evaluated,
    StructureKeys.generateManagedSourcesDuringStructureDump := retrieveGenerateManagedSources.getOrElse(true)
  ) ++ CreateTasks.globalSettings

  /**
   * Reads a system property by name, trimming whitespace and removing surrounding double quotes
   * if present. Empty values are ignored.
   */
  private def getSysProp(name: String): Option[String] =
    sys.props.get(name)
      .map(_.trim)
      .map { s =>
        if (s.length >= 2 && s.head == '"' && s.last == '"') s.substring(1, s.length - 1).trim
        else s
      }
      .filter(_.nonEmpty)

  /**
   * Optional output file for the `dumpStructure` task, taken from the
   * `sbt.structure.outputFile` system property if defined.
   */
  private def retrieveOutputFile: Option[File] =
    getSysProp("sbt.structure.outputFile").map(file)

  /**
   * Optional command-line style options for `dumpStructure`, taken from the
   * `sbt.structure.options` system property if defined.
   */
  private def retrieveOptions: Option[String] =
    getSysProp("sbt.structure.options")

  /**
   * Whether to generate managed sources during `dumpStructure`, taken from the
   * `sbt.structure.generateManagedSources` system property (boolean) if defined.
   */
  private def retrieveGenerateManagedSources: Option[Boolean] =
    getSysProp("sbt.structure.generateManagedSources").flatMap(s => Try(s.toBoolean).toOption)

  override lazy val projectSettings: Seq[Setting[?]] = CreateTasks.projectSettings.toSbtSeqType
}
