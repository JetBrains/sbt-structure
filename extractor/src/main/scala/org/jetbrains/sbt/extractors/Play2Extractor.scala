package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.{SbtStateOps, TaskOps}
import org.jetbrains.sbt.structure._
import sbt._
import sbt.jetbrains.PluginCompat._

object Play2Extractor extends SbtStateOps with TaskOps {

  def taskDef: Def.Initialize[Task[Option[Play2Data]]] = Def.task {
    val state = sbt.Keys.state.value
    val projectRef = sbt.Keys.thisProjectRef.value

    for {
      _ <- (projectRef / Keys.playPlugin).find(state)
        .orElse((projectRef / Keys.playPlugin_prior_to_2_4_0).find(state))
      sourceDirectory <- (projectRef / Compile / sbt.Keys.sourceDirectory).find(state)
    } yield {
      val playVersion =
        (projectRef / Keys.playVersion).find(state)
      val templateImports =
        (projectRef / Keys.templateImports).getValueOrElse(state, Seq.empty)
      val routesImports =
        (projectRef / Keys.routesImports).find(state)
          .orElse((projectRef / Keys.routesImports_prior_to_2_4_0).find(state))
          .getOrElse(Seq.empty)
      val confDirectory =
        (projectRef / Keys.confDirectory).find(state)

      Play2Data(playVersion, fixTemplateImports(templateImports),
        routesImports, confDirectory, sourceDirectory)
    }
  }

  private def fixTemplateImports(imports: Seq[String]): Seq[String] = imports.map {
    case "views.%format%._" => "views.xml._"
    case value => value
  }

  private object Keys {
    val playPlugin_prior_to_2_4_0: SettingKey[Boolean] = SettingKey[Boolean]("play-plugin")
    val playPlugin: SettingKey[Boolean] = SettingKey[Boolean]("playPlugin")
    val playVersion: SettingKey[String] = SettingKey[String]("play-version")
    val templateImports: SettingKey[Seq[String]] = SettingKey[Seq[String]]("twirl-template-imports")
    val routesImports_prior_to_2_4_0: SettingKey[Seq[String]] = SettingKey[Seq[String]]("play-routes-imports")
    val routesImports: SettingKey[Seq[String]] = SettingKey[Seq[String]]("playRoutesImports")
    val confDirectory: SettingKey[File] = SettingKey[File]("play-conf")
  }
}
