package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.StructureExtractor

import scala.xml.PrettyPrinter
import java.io.{OutputStreamWriter, BufferedWriter, FileOutputStream}
import sbt._

import structure.XmlSerializer._

/**
 * @author Nikolay Obedin
 */

object CreateTasks extends (State => State) with SbtStateOps {
  def apply(state: State) = {
    val globalSettings = Seq[Setting[_]](
      StructureKeys.sbtStructureOpts <<=
        StructureKeys.sbtStructureOptions.apply(Options.readFromString),
      StructureKeys.dumpStructure <<=
        Keys.state.map(s => read(s))
    )
    applySettings(state, globalSettings, Nil)
  }

  private def applySettings(state: State, globalSettings: Seq[Setting[_]], projectSettings: Seq[Setting[_]]): State = {
    val extracted = Project.extract(state)
    import extracted._
    import extracted.{structure => extractedStructure}
    val transformedGlobalSettings = Project.transform(_ => GlobalScope, globalSettings)
    val transformedProjectSettings = extractedStructure.allProjectRefs.flatMap { projectRef =>
      Load.transformSettings(Load.projectScope(projectRef), projectRef.build, rootProject, projectSettings)
    }
    val newStructure = Load.reapply(session.mergeSettings ++ transformedGlobalSettings ++ transformedProjectSettings, extractedStructure)
    Project.setProject(session, newStructure, state)
  }

  private def read(implicit state: State) {
    val log = state.log

    log.info("Reading structure from " + System.getProperty("user.dir"))

    val options = setting(StructureKeys.sbtStructureOpts).getOrElse(Options.default)
    val structure = StructureExtractor.apply(state, options)

    val text = {
      if (options.prettyPrint)
        new PrettyPrinter(180, 2).format(structure.serialize)
      else
        xml.Utility.trim(structure.serialize).mkString
    }

    setting(StructureKeys.sbtStructureOutputFile).flatten.map { file =>
      log.info("Writing structure to " + file.getPath + "...")
      write(file, text)
      log.info("Done.")
    } getOrElse {
      log.info("Writing structure to console:")
      println(text)
      log.info("Done.")
    }
  }

  private def write(file: File, xml: String) {
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))
    try {
      writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
      writer.newLine()
      writer.write(xml)
      writer.flush()
    } finally {
      writer.close()
    }
  }
}
