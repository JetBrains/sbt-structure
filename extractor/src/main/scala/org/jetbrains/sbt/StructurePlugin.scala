package org.jetbrains.sbt

import org.jetbrains.sbt.extractors.Extractor.Options
import org.jetbrains.sbt.extractors.StructureExtractor

import scala.xml.PrettyPrinter
import java.io.{OutputStreamWriter, BufferedWriter, FileOutputStream}
import sbt._
import Keys._

import structure.XmlSerializer._

/**
 * @author Pavel Fatin
 */
object StructurePlugin extends Plugin {
  def read(state: State) {
    val log = state.log

    log.info("Reading structure from " + System.getProperty("user.dir"))

    val options = Keys.artifactClassifier.in(Project.current(state))
      .get(Project.extract(state).structure.data).get.getOrElse("")

    val (download, resolveClassifiers, resolveSbtClassifiers, prettyPrint, cachedUpdate) =
      (options.contains("download"), options.contains("resolveClassifiers"),
        options.contains("resolveSbtClassifiers"), options.contains("prettyPrint"),
        options.contains("cachedUpdate"))

    val structure = StructureExtractor.extract(state, Options(download, resolveClassifiers, resolveSbtClassifiers, cachedUpdate)).get

    val text = {
      val home = new File(System.getProperty("user.home"))
      val base = new File(System.getProperty("user.dir"))
      if (prettyPrint)
        new PrettyPrinter(180, 2).format(structure.serialize)
      else
        xml.Utility.trim(structure.serialize).mkString
    }

    Keys.artifactPath.in(Project.current(state)).get(Project.extract(state).structure.data).map { file =>
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

  override lazy val settings: Seq[Setting[_]] = Seq(commands += readProjectCommand)

  lazy val readProjectCommand = Command.command("read-project")((s: State) => ReadProject(s))
}

object ReadProject extends (State => State) {
  def apply(state: State) = Function.const(state)(StructurePlugin.read(state))
}
