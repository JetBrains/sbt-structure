package org.jetbrains.sbt.sources

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardCopyOption}

import org.jetbrains.sbt.runtime.{SbtStateOps, UpdateReportAdapter}
import sbt._

import scala.collection.Seq
import scala.xml.Elem

/** Sources resolved for the sbt runtime and build-definition plugins of loaded builds. */
final case class SbtSourcesReport(builds: Seq[SbtBuildSources]) {
  def normalized: SbtSourcesReport =
    SbtSourcesReport(builds.map(_.normalized).sortBy(_.uri.toString))
}

final case class SbtBuildSources(uri: URI, sources: Seq[File]) {
  def normalized: SbtBuildSources =
    SbtBuildSources(
      uri.normalize,
      sources.map(_.getCanonicalFile).distinct.sortBy(_.getPath)
    )
}

object SbtSourcesReport {
  val FormatVersion = "1"

  def fromUpdateReports(reports: Seq[(ProjectRef, UpdateReportAdapter)]): SbtSourcesReport = {
    val builds = reports.map { case (projectRef, updateReport) =>
      val sources = updateReport.allModules.flatMap(_.artifacts.collect {
        case (artifact, source) if artifact.`type` == Artifact.SourceType => source
      })
      SbtBuildSources(projectRef.build, sources)
    }
    SbtSourcesReport(builds).normalized
  }
}

object SbtSourcesXml {
  def serialize(report: SbtSourcesReport): Elem =
    <sbtSources version={SbtSourcesReport.FormatVersion}>{
      report.normalized.builds.map { build =>
        <build>
          <uri>{build.uri.toString}</uri>{
          build.sources.map(source => <source>{source.getPath}</source>)}
        </build>
      }
    }</sbtSources>

  def render(report: SbtSourcesReport): String =
    xml.Utility.trim(serialize(report)).mkString
}

private[sbt] object SbtSourcesTask extends SbtStateOps {
  val taskDef: Def.Initialize[Task[SbtSourcesReport]] = Def.taskDyn {
    val state = Keys.state.value
    Def.task {
      resolve(state).value
    }
  }

  def resolve(state: State): Task[SbtSourcesReport] = {
    val rootProjects = structure(state).units.toSeq
      .sortBy { case (uri, _) => uri.toString }
      .map { case (uri, unit) =>
        val rootProject = unit.rootProjects.headOption.getOrElse {
          sys.error("Loaded sbt build has no root project: " + uri)
        }
        ProjectRef(uri, rootProject)
      }

    sbt.Keys.updateSbtClassifiers
      .forAllProjects(state, rootProjects)
      .map { reports =>
        SbtSourcesReport.fromUpdateReports(reports.toSeq.map { case (projectRef, report) =>
          projectRef -> new UpdateReportAdapter(report)
        })
      }
  }
}

private[sbt] object AtomicFileWriter {
  def writeUtf8(file: File, text: String): Unit = {
    val target = file.toPath.toAbsolutePath.normalize
    val parent = Option(target.getParent).getOrElse {
      throw new IllegalArgumentException("The response file has no parent directory: " + file)
    }
    val temporary = Files.createTempFile(parent, ".sbt-sources-", ".tmp")

    var moved = false
    try {
      Files.write(temporary, text.getBytes(StandardCharsets.UTF_8))
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      moved = true
    } finally {
      if (!moved)
        Files.deleteIfExists(temporary)
    }
  }
}
