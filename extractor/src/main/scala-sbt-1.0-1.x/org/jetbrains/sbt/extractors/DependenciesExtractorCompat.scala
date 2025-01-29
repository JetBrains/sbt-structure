package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.ModulesOps
import org.jetbrains.sbt.structure.*
import sbt.{Keys, Configuration as SbtConfiguration}

import scala.collection.Seq
import scala.language.postfixOps

object DependenciesExtractorCompat extends ModulesOps {

  def modulesIn(configuration: SbtConfiguration, externalDependencyClasspath: Option[SbtConfiguration => Keys.Classpath]): Seq[ModuleIdentifier] =
    for {
      classpathFn <- externalDependencyClasspath.toSeq
      entry       <- classpathFn(configuration)
      moduleId    <- entry.get(Keys.moduleID.key).toSeq
      artifact    <- entry.get(Keys.artifact.key).toSeq
      identifier  <- createModuleIdentifiers(moduleId, Seq(artifact))
    } yield {
      identifier
    }
}
