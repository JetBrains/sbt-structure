package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure._
import sbt._

/**
  * @author Dmitry Naydanov
  * @author Nikolay Obedin
  */
class Play2Extractor(implicit projectRef: ProjectRef) extends SbtStateOps {

  import Play2Extractor._

  private def extract(implicit state: State): Option[Play2Data] = {
    for {
      _ <- projectSetting(Keys.playPlugin).orElse(projectSetting(Keys.playPlugin_prior_to_2_4_0))
      sourceDirectory <- projectSetting(sbt.Keys.sourceDirectory.in(Compile))
    } yield {
      val playVersion = projectSetting(Keys.playVersion)
      val templateImports = projectSetting(Keys.templateImports).getOrElse(Seq.empty)
      val routesImports = projectSetting(Keys.routesImports)
        .orElse(projectSetting(Keys.routesImports_prior_to_2_4_0))
        .getOrElse(Seq.empty)
      val confDirectory = projectSetting(Keys.confDirectory)
      Play2Data(playVersion, fixTemplateImports(templateImports), routesImports, confDirectory, sourceDirectory)
    }
  }

  private def fixTemplateImports(imports: Seq[String]): Seq[String] = imports.map {
    case "views.%format%._" => "views.xml._"
    case value => value
  }
}

object Play2Extractor {
  def apply(implicit state: State, projectRef: ProjectRef): Option[Play2Data] =
    new Play2Extractor().extract

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