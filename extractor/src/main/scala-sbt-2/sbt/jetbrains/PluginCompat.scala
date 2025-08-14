package sbt.jetbrains

import sbt.*

object PluginCompat extends AnyRef
  with SeqOpsCompat
  with ClassathOpsCompat
  with PluginCompat_SinceSbt_1_3:

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

  val globalSettingsSbtSpecific: Seq[Setting[?]] = Seq(
    /**
     * This is required to mute working for the unused key defined in<br>
     * [[org.jetbrains.sbt.CreateTasks.projectSettings]]
     */
    Keys.excludeLintKeys += Keys.updateClassifiers / Keys.transitiveClassifiers,
  )
end PluginCompat
