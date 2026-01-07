package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.UtilityTasks.writeToFile
import org.jetbrains.sbt.structure.XmlSerializer._
import sbt._
import sbt.complete.DefaultParsers
import sbt.plugins.JvmPlugin

//The class is used indirectly by Scala Plugin (see also SCL-20353)
//noinspection ScalaUnusedSymbol
object StructurePlugin extends AutoPlugin {

  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    StructureKeys.sbtStructureOutputFile := retrieveOutputFile.orElse(None),
    StructureKeys.sbtStructureOptions := retrieveOptions.getOrElse("prettyPrint download"),
    StructureKeys.dumpStructureTo := pluginOnlyTasks.dumpStructureTo.evaluated,
    StructureKeys.generateManagedSourcesDuringStructureDump := retrieveGenerateManagedSources.getOrElse(true)
  ) ++ CreateTasks.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = CreateTasks.projectSettings

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
    getSysProp("sbt.structure.generateManagedSources").map(_.toBoolean)
}

private object pluginOnlyTasks {

  private val targetFileParser = DefaultParsers.fileParser(file("/"))


  // this task is not compatible with old sbt versions (0.13.0) and only available as part of the StructurePlugin
  lazy val dumpStructureTo: Def.Initialize[InputTask[File]] = Def.inputTaskDyn {

    val outputFile = targetFileParser.parsed
    val options = StructureKeys.sbtStructureOpts.value

    val log = Keys.streams.value.log
    val structureTask = extractors.extractStructure

    Def.task {
      val structure = structureTask.value.serialize
      val outputText = {
        if (options.prettyPrint) newXmlPrettyPrinter.format(structure)
        else xml.Utility.trim(structure).mkString
      }

      log.info("Writing structure to " + outputFile.getPath + "...")
      // noinspection UnitInMap
      writeToFile(outputFile, outputText)
      log.info("Done.")
      outputFile
    }
  }
}
