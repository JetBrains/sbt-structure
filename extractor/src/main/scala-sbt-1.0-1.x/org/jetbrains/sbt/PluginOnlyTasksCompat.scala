package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.UtilityTasks.writeToFile
import org.jetbrains.sbt.structure.XmlSerializer.*
import org.jetbrains.sbt.structure.structureDataSerializer
import sbt.*
import sbt.complete.DefaultParsers

private object PluginOnlyTasksCompat {

  private val targetFileParser = DefaultParsers.fileParser(file("/"))

  lazy val dumpStructureTo: Def.Initialize[InputTask[File]] = Def.inputTaskDyn {
    val outputFile = targetFileParser.parsed
    val options = StructureKeys.sbtStructureOpts.value

    val log = Keys.streams.value.log
    val extractStructure = extractors.extractStructure

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
  }
}