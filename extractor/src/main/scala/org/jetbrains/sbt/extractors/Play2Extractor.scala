package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import sbt._
import sbt.Project.Initialize

/**
  * @author Dmitry Naydanov
  * @author Nikolay Obedin
  */
object Play2Extractor extends SbtStateOps with TaskOps {

  def taskDef: Initialize[Task[Option[Play2Data]]] =
    (sbt.Keys.state, sbt.Keys.thisProjectRef) map { (state, projectRef) =>
      for {
        _ <- Keys.playPlugin.in(projectRef).find(state)
               .orElse(Keys.playPlugin_prior_to_2_4_0.in(projectRef).find(state))
        sourceDirectory <- sbt.Keys.sourceDirectory.in(projectRef, Compile).find(state)
      } yield {
        val playVersion =
          Keys.playVersion.in(projectRef).find(state)
        val templateImports =
          Keys.templateImports.in(projectRef).getOrElse(state, Seq.empty)
        val routesImports =
          Keys.routesImports.in(projectRef).find(state)
          .orElse(Keys.routesImports_prior_to_2_4_0.in(projectRef).find(state))
          .getOrElse(Seq.empty)
        val confDirectory =
          Keys.confDirectory.in(projectRef).find(state)

        Play2Data(playVersion, fixTemplateImports(templateImports),
          routesImports, confDirectory, sourceDirectory)
      }
    }

  private def fixTemplateImports(imports: Seq[String]): Seq[String] = imports.map {
    case "views.%format%._" => "views.xml._"
    case value => value
  }

  private object Keys {
    val playPlugin_prior_to_2_4_0 = SettingKey[Boolean]("play-plugin")
    val playPlugin = SettingKey[Boolean]("playPlugin")
    val playVersion = SettingKey[String]("play-version")
    val templateImports = SettingKey[Seq[String]]("twirl-template-imports")
    val routesImports_prior_to_2_4_0 = SettingKey[Seq[String]]("play-routes-imports")
    val routesImports = SettingKey[Seq[String]]("playRoutesImports")
    val confDirectory = SettingKey[File]("play-conf")
  }
}
