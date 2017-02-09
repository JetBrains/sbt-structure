package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.structure.{CommandData, SettingData, TaskData}
import sbt.{AttributeKey, BuiltinCommands, Command, Def, Keys, Project, SettingKey, Task}
import sbt.jetbrains.BadCitizen


/**
  * Extract setting and task keys.
  * For instance, settings could show a stringified form directly.
  */
object KeysExtractor {

  val maxValueStringLength = 103

  def allKeys: Def.Initialize[Task[Seq[AttributeKey[_]]]] = Def.task {
    BuiltinCommands.allTaskAndSettingKeys(Keys.state.value)
  }

  def settingData: Def.Initialize[Task[Seq[SettingData]]] = Def.task {
    val state = Keys.state.value
    val extracted = Project.extract(state)
    allKeys.value
      .filterNot(a => BuiltinCommands.isTask(a.manifest))
      .map { k =>
        val stringValue = for {
          value <- extracted.getOpt(SettingKey(k))
          // only get a display string if it has a chance of being meaningful to the user, ie is redefined
          if value.getClass.getMethod("toString").getDeclaringClass ne classOf[Any]
        } yield {
          val stringified = value.toString.trim
          if (stringified.length > maxValueStringLength)
            stringified.substring(0, maxValueStringLength-3) + "..."
          else stringified
        }
        SettingData(k.label, k.description, k.rank, stringValue)
      }
  }

  // TODO filter sbtStructure-specific keys
  def taskData: Def.Initialize[Task[Seq[TaskData]]] = Def.task {
    allKeys.value
      .filter(a => BuiltinCommands.isTask(a.manifest))
      .map { k => TaskData(k.label, k.description, k.rank) }
  }


  /**
    * Finds all named commands
    * @return
    */
  def commandData: Def.Initialize[Task[Seq[CommandData]]] = Def.task {
    val state = Keys.state.value
    val commands = state.definedCommands
    for {
      command <- commands
      name <- BadCitizen.commandName(command)
    } yield CommandData(name, command.help(state).brief)
  }

}
