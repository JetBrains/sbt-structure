package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.ModulesOps
import org.jetbrains.sbt.structure.*
import sbt.Classpaths.moduleIdJsonKeyFormat
import sbt.librarymanagement.Artifact
import sbt.{Keys, Configuration as SbtConfiguration}
import sjsonnew.JsonFormat

import scala.collection.Seq
import scala.language.postfixOps

object DependenciesExtractorCompat extends ModulesOps {

  def modulesIn(configuration: SbtConfiguration, externalDependencyClasspath: Option[SbtConfiguration => Keys.Classpath]): Seq[ModuleIdentifier] =
    for {
      classpathFn <- externalDependencyClasspath.toSeq
      entry       <- classpathFn(configuration)
      moduleIdStr <- entry.get(sbt.Keys.moduleIDStr).toSeq
      artifactStr <- entry.get(sbt.Keys.artifactStr).toSeq
      moduleId    = moduleIdJsonKeyFormat.read(moduleIdStr)
      artifact    = artifactFromStr(artifactStr)
      identifier  <- createModuleIdentifiers(moduleId, Seq(artifact))
    } yield {
      identifier
    }

  /**
   * Dual for [[sbt.internal.RemoteCache.artifactToStr]]
   * @todo Ask SBT to create an utility for that?
   *       We have moduleIdJsonKeyFormat but not for Artifact, why?
   */
  private def artifactFromStr(artJsonStr: String): Artifact = {
    import sbt.librarymanagement.LibraryManagementCodec.given
    import sjsonnew.support.scalajson.unsafe.*
    val format: JsonFormat[Artifact] = summon[JsonFormat[Artifact]]
    val artJson = Parser.parseFromString(artJsonStr).get
    Converter.fromJson[Artifact](artJson).get
  }
}
