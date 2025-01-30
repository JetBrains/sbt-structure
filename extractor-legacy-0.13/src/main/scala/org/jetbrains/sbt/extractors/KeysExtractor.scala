package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.StructureKeys
import org.jetbrains.sbt.structure.{CommandData, SettingData, TaskData}
import sbt.jetbrains.BadCitizen
import sbt.{AttributeKey, BuiltinCommands, Def, Extracted, KeyRanks, Keys, Logger, Project, SettingKey, Task}

import scala.util.control.NonFatal


/**
  * Extract setting and task keys.
  * For instance, settings could show a stringified form directly.
  */
object KeysExtractor {

  /** Maximum length of toString'ed setting value exported. */
  val maxValueStringLength = 103

  val allKeys: Def.Initialize[Task[Seq[AttributeKey[_]]]] = Def.task (
    try {
      BuiltinCommands
        .allTaskAndSettingKeys(Keys.state.value)
        .filter(a => a.rank < KeyRanks.Invisible) // not interested in importing implementation details
    } catch {
      // prior to sbt 0.13.9, this error is not caught, so mitigate it here in case users are affected
      case NonFatal(x) =>
        Keys.state.value.log.warn(s"Unable to import task and setting keys. Upgrade your project sbt to 0.13.11 or later. Error was: ${x.getMessage}")
        Seq.empty
    }
  )

  val settingData: Def.Initialize[Task[Seq[SettingData]]] = Def.task {
    val state = Keys.state.value
    val extracted = Project.extract(state)

    guarded(Keys.state.value.log, Seq.empty[SettingData]) {
      for {
        key <- StructureKeys.allKeys.value
        if key != null && ! BuiltinCommands.isTask(key.manifest)
      } yield {
        val displayString = settingStringValue(extracted,key)
        SettingData(key.label, key.description, key.rank, displayString)
      }
    }
  }

  val taskData: Def.Initialize[Task[Seq[TaskData]]] = Def.task {

    guarded(Keys.state.value.log, Seq.empty[TaskData]) {
      for {
        key <- StructureKeys.allKeys.value
        if key != null && BuiltinCommands.isTask(key.manifest)
      } yield
        TaskData(key.label, key.description, key.rank)
    }
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


  private def settingStringValue(extracted: Extracted, key: AttributeKey[_]) = for {
    value <- extracted.getOpt(SettingKey(key))
    // only get a display string if it has a chance of being meaningful to the user, ie is redefined
    if value != null && (value.getClass.getMethod("toString").getDeclaringClass ne classOf[Any])
    stringValue <- Option(value.toString) // some zany settings might return a null toString
  } yield {
    val trimmed = stringValue.toString.trim
    if (trimmed.length > maxValueStringLength)
      trimmed.substring(0, maxValueStringLength - 3) + "..."
    else trimmed
  }

  private def guarded[T](log: Logger, default: =>T)(f: =>T): T = {
    try {f}
    catch {
      case NonFatal(x) =>
        log.warn(
          s"Unable to import some setting or task data. Is your project configured correctly?\n" +
            s"Error was: ${x.getMessage}\n${x.getStackTraceString}")
        default
    }
  }

}
