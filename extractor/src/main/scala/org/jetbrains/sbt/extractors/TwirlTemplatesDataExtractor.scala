package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.{SbtStateOps, TaskOps}
import sbt._

object TwirlTemplatesDataExtractor extends SbtStateOps with TaskOps {

  def taskDef: Def.Initialize[Task[Option[TwirlData]]] = Def.task {
    val state = sbt.Keys.state.value
    val projectRef = sbt.Keys.thisProjectRef.value

    for {
      templateImports <- Keys.templateImports.in(projectRef).find(state)
    } yield {
      val templateImportsFixed = fixTemplateImports(templateImports)
      TwirlData(templateImportsFixed)
    }
  }

  private def fixTemplateImports(imports: Seq[String]): Seq[String] = imports.map {
    case "views.%format%._" => "views.xml._"
    case value => value
  }

  private object Keys {
    // should match play.twirl.sbt.Import.TwirlKeys#templateImports
    // (see https://github.com/playframework/twirl/blob/main/sbt-twirl/src/main/scala/play/twirl/sbt/SbtTwirl.scala)
    val templateImports: SettingKey[Seq[String]] = SettingKey[Seq[String]]("twirl-template-imports")
  }
}
