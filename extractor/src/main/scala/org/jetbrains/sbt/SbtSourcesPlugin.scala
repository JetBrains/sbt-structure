package org.jetbrains.sbt

import java.io.File

import org.jetbrains.sbt.sources.{AtomicFileWriter, SbtSourcesTask}
import sbt.complete.DefaultParsers
import sbt.jetbrains.PluginCompat._
import sbt._

/**
 * Resolves the source archives used by sbt and build-definition plugins without extracting project structure.
 *
 * This plugin is intentionally standalone so IDE clients can load it with `-addPluginSbtFile`.
 */
// The class is used indirectly by the Scala plugin.
//noinspection ScalaUnusedSymbol
object SbtSourcesPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    @transient val dumpSbtSourcesTo = inputKey[File]("Resolve SBT and build-plugin sources to the specified XML file")
  }

  import autoImport.dumpSbtSourcesTo

  private val targetFileParser = DefaultParsers.fileParser(file("/"))
  @transient private val resolveSbtSources = taskKey[SbtSourcesReport]("Resolve sources for SBT and build-definition plugins")

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    Keys.updateSbtClassifiers / Keys.transitiveClassifiers := Seq(Artifact.SourceClassifier).toSbtSeqType
  )

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    resolveSbtSources := SbtSourcesTask.taskDef.value,
    dumpSbtSourcesTo := {
      val outputFile = targetFileParser.parsed
      val log = Keys.streams.value.log

      log.info("Resolving SBT and build-plugin sources...")
      val report = resolveSbtSources.value
      AtomicFileWriter.writeUtf8(outputFile, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + SbtSourcesXml.render(report))
      log.info("Wrote SBT sources to " + outputFile.getPath)
      outputFile
    }
  )
}
