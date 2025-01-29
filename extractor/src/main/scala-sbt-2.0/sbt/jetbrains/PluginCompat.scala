package sbt.jetbrains

import sbt.*

object PluginCompat extends AnyRef
  with SeqOpsCompat
  with ClassathOpsCompat:

  type SbtSettings = Settings

  def extractProject(state: sbt.State): sbt.Extracted = {
    import ProjectExtra.extract
    sbt.Project.extract(state)
  }

  def isTaskOrInputTask(attributeKey: AttributeKey[_]): Boolean =
    attributeKey.tag.isTaskOrInputTask

  def throwExceptionIfUpdateFailed(result: Result[Map[sbt.Configuration,Keys.Classpath]]): Map[sbt.Configuration, Keys.Classpath] =
    result match {
      case Result.Value(classpath) =>
        classpath
      case Result.Inc(incomplete) =>
        val cause = Incomplete.allExceptions(incomplete).headOption
        cause.foreach(c => throw c)
        Map.empty
    }
end PluginCompat
