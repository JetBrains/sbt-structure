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
    StructureKeys.sbtStructureOutputFile := None,
    StructureKeys.sbtStructureOptions := "prettyPrint download",
    StructureKeys.dumpStructureTo := pluginOnlyTasks.dumpStructureTo.evaluated
  ) ++ CreateTasks.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = CreateTasks.projectSettings
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
