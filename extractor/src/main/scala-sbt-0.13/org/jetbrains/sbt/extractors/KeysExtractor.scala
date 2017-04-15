package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.StructureKeys
import org.jetbrains.sbt.structure.{CommandData, SettingData, TaskData}
import sbt.jetbrains.BadCitizen
import sbt.{AttributeKey, BuiltinCommands, Def, Keys, Project, SettingKey, Task}


/**
  * Extract setting and task keys.
  * For instance, settings could show a stringified form directly.
  */
object KeysExtractor {

  /** Maximum length of toString'ed setting value exported. */
  val maxValueStringLength = 103

  val allKeys: Def.Initialize[Task[Seq[AttributeKey[_]]]] = Def.task {
    BuiltinCommands.allTaskAndSettingKeys(Keys.state.value)
  }

  val settingData: Def.Initialize[Task[Seq[SettingData]]] = Def.task {
    val state = Keys.state.value
    val extracted = Project.extract(state)
    StructureKeys.allKeys.value
      .filterNot(a => BuiltinCommands.isTask(a.manifest))
      .map { k =>
        val stringValue = for {
          value <- extracted.getOpt(SettingKey(k))
          if value != null
          // only get a display string if it has a chance of being meaningful to the user, ie is redefined
          if value.getClass.getMethod("toString").getDeclaringClass ne classOf[Any]
          stringValue <- Option(value.toString) // some zany settings might return a null toString
        } yield {
          val stringified = stringValue.toString.trim

          if (stringified.length > maxValueStringLength)
              stringified.substring(0, maxValueStringLength-3) + "..."
          else stringified
        }
        SettingData(k.label, k.description, k.rank, stringValue)
      }
  }

  val taskData: Def.Initialize[Task[Seq[TaskData]]] = Def.task {
    StructureKeys.allKeys.value
      .filter(a => BuiltinCommands.isTask(a.manifest))
      .map { k => TaskData(k.label, k.description, k.rank) }
  }


  /**
    * Finds all named commands
    * @return
    */
  val commandData: Def.Initialize[Task[Seq[CommandData]]] = Def.task {
    val state = Keys.state.value
    val commands = state.definedCommands
    for {
      command <- commands
      name <- BadCitizen.commandName(command)
    } yield CommandData(name, command.help(state).brief)
  }

}
