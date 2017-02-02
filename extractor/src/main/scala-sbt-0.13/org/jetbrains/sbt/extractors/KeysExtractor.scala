package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.structure.{SettingData, TaskData}
import sbt.{AttributeKey, BuiltinCommands, Command, Def, Keys, Task}
import sbt.jetbrains.BadCitizen


/**
  * Extract setting and task keys.
  * Use different data formats, because settings and tasks may be treated differently by UI, and extended in different ways.
  * For instance, settings could show a stringified form directly.
  */
object KeysExtractor {

  def allKeys: Def.Initialize[Task[Seq[AttributeKey[_]]]] = Def.task {
    BuiltinCommands.allTaskAndSettingKeys(Keys.state.value)
  }

  def settingKeys: Def.Initialize[Task[Seq[SettingData]]] = Def.task {
    allKeys.value
      .filterNot(a => BuiltinCommands.isTask(a.manifest))
      .map { k => SettingData(k.label, k.description, k.rank) }
  }

  def taskKeys: Def.Initialize[Task[Seq[TaskData]]] = Def.task {
    allKeys.value
      .filter(a => BuiltinCommands.isTask(a.manifest))
      .map { k => TaskData(k.label, k.description, k.rank) }
  }

  /**
    * Finds all named commands
    * @return
    */
  def allNamedCommands: Def.Initialize[Task[Seq[Command]]] = Def.task {
    val commands = Keys.state.value.definedCommands
    commands.filterNot(BadCitizen.isSimpleCommand)
  }

}
