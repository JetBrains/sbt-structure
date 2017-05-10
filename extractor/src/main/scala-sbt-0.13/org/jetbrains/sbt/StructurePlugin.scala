package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.UtilityTasks.writeToFile
import sbt._
import sbt.plugins.JvmPlugin
import org.jetbrains.sbt.structure.XmlSerializer._
import sbt.complete.DefaultParsers._
import sbt.complete.{DefaultParsers, Parser}

import scala.xml.PrettyPrinter

/**
  * Created by jast on 2017-02-22.
  */
object StructurePlugin extends AutoPlugin {

  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    StructureKeys.sbtStructureOutputFile := None, // TODO deprecate for plugin use
    StructureKeys.sbtStructureOptions := "prettyPrint download", // TODO deprecate for plugin use
      StructureKeys.dumpStructureTo := pluginOnlyTasks.dumpStructureTo.evaluated
  ) ++ CreateTasks.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = CreateTasks.projectSettings

}

private object pluginOnlyTasks {

  private val optParser: Parser[Seq[String]] =
    (' ' ~> token(literal("prettyPrint") | "download" | "resolveClassifiers" | "resolveJavadocs" | "resolveSbtClassifiers")).*
      .map(_.distinct)
  private val fileOptParser = DefaultParsers.fileParser(file("/")) ~ optParser

  // this task is not compatible with old sbt versions (0.13.0) and only interesting as part of the plugin
  lazy val dumpStructureTo: Def.Initialize[InputTask[File]] = Def.inputTaskDyn {

    val (outputFile, params) = fileOptParser.parsed
    val options = Options.readFromSeq(params)
    val log = Keys.streams.value.log
    val structureTask = extractors.extractStructure(options)

    Def.task {
      val structure = structureTask.value.serialize
      val outputText = {
        if (options.prettyPrint) new PrettyPrinter(180, 2).format(structure)
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