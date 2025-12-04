package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.UtilityTasks.writeToFile
import org.jetbrains.sbt.structure.XmlSerializer._
import sbt._
import sbt.complete.DefaultParsers
import org.jetbrains.sbt.structure.structureDataSerializer
import sbt.jetbrains.PluginCompat

private object PluginOnlyTasksCompat {

  private val targetFileParser = DefaultParsers.fileParser(file("/"))

  // this task is not compatible with old sbt versions (0.13.0) and only available as part of the StructurePlugin
  lazy val dumpStructureTo: Def.Initialize[InputTask[File]] = Def.inputTaskDyn {
    val outputFile = targetFileParser.parsed
    val options = StructureKeys.sbtStructureOpts.value

    val log = Keys.streams.value.log

    val isFailedReload = PluginCompat.isFailedReload.value
    if (!isFailedReload) {
      Def.task {
        val structure = extractors.extractStructure.value.serialize
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
    } else {
      Def.task { outputFile }
    }
  }
}