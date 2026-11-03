package org.jetbrains.sbt.dump.extract

import org.jetbrains.sbt.SbtStateOps
import org.jetbrains.sbt.structure.TwirlData
import sbt._

object TwirlExtractor extends SbtStateOps {

  def taskDef: Def.Initialize[Task[Option[TwirlData]]] = Def.task {
    val state = sbt.Keys.state.value
    val projectRef = sbt.Keys.thisProjectRef.value
    val templateImports = Keys.templateImports.in(projectRef, Compile).find(state).map(fixTemplateImports)
    templateImports.map(TwirlData.apply)
  }

  private def fixTemplateImports(imports: Seq[String]): Seq[String] = imports.map {
    case "views.%format%._" => "views.xml._"
    case value => value
  }

  private object Keys {
    val templateImports: SettingKey[Seq[String]] = SettingKey("twirl-template-imports")
  }
}
