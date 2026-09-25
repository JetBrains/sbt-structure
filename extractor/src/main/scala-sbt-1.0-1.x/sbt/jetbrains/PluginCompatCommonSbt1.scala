package sbt.jetbrains

import org.jetbrains.sbt.compat.FileConverterCompat
import sbt.{AttributeKey, Def, Incomplete, InputTask, Keys, Result, Scope, Setting, Settings, Task}

trait PluginCompatCommonSbt1 extends SeqOpsCompat with ClasspathOpsCompat {

  type SbtSettings = Settings[Scope]

  def extractProject(state: sbt.State): sbt.Extracted =
    sbt.Project.extract(state)

  def isTaskOrInputTask(
    attributeKey: AttributeKey[_]
  )(implicit taskMF: Manifest[Task[_]], inputMF: Manifest[InputTask[_]]): Boolean = {
    val mf = attributeKey.manifest
    mf.runtimeClass == taskMF.runtimeClass || mf.runtimeClass == inputMF.runtimeClass
  }

  def throwExceptionIfUpdateFailed(result: Result[Map[sbt.Configuration,Keys.Classpath]]): Map[sbt.Configuration, Keys.Classpath] =
    result match {
      case sbt.Value(classpath) =>
        classpath
      case sbt.Inc(incomplete) =>
        val cause = Incomplete.allExceptions(incomplete).headOption
        cause.foreach(c => throw c)
        Map.empty
    }

    val globalSettingsSbtSpecific: Seq[Setting[?]] = Nil

  /**
   * In sbt 1.x, `scalacOptions` already contains absolute `-Xplugin` paths, so there is nothing to
   * resolve, and we just return the options as-is.
   */
  def scalacOptionsResolver: Def.Initialize[Task[Seq[String] => Seq[String]]] =
    Def.task((options: Seq[String]) => options)

  def fileConverterCompat: Def.Initialize[FileConverterCompat] =
    Def.setting(FileConverterCompat())
}
