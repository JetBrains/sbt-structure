package org.jetbrains.sbt.dump.compat

import org.jetbrains.sbt.{StructureKeys, newXmlPrettyPrinter}
import org.jetbrains.sbt.dump.workflow.UtilityTasks.writeToFile
import org.jetbrains.sbt.structure.XmlSerializer.*
import org.jetbrains.sbt.structure.structureDataSerializer
import sbt.*
import sbt.complete.DefaultParsers
import sbt.jetbrains.PluginCompat

private[sbt] object PluginOnlyTasksCompat {

  private val targetFileParser = DefaultParsers.fileParser(file("/"))

  lazy val dumpStructureTo: Def.Initialize[InputTask[File]] = Def.inputTaskDyn {
    val outputFile = targetFileParser.parsed
    val options = StructureKeys.sbtStructureOpts.value

    val log = Keys.streams.value.log
    val extractStructure = org.jetbrains.sbt.extractors.extractStructure

    val isFailedReload = PluginCompat.isFailedReload.value
    if (!isFailedReload) {
      Def.task {
        val structure = extractStructure.value.serialize
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
