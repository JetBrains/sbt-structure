package sbt.jetbrains

import sbt.*
import scala.util.Try

object PluginCompat extends SeqOpsCompat with ClassathOpsCompat with CoursierLoggerSettingsCompat:

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

  private val oldScalaCompilerBridgeBinaryJarImpl: Def.Initialize[Task[Option[File]]] = {
    import sbt.internal.inc.ZincLmUtil
    import sbt.Keys.*

    // This is the implementation of `scalaCompilerBridgeBinaryJarImpl` in sbt 1.3+ up to 2.0.0-RC7
    // when the task was removed and replaced with a cached version.
    // https://github.com/sbt/sbt/commit/68b2b7d0251d9bf352739f904d64590e9e9e8396

    Def.task {
      val sv = scalaVersion.value
      val managed = managedScalaInstance.value
      val hasSbtBridge = ScalaArtifacts.isScala3(sv) || ZincLmUtil.hasScala2SbtBridge(sv)
      if hasSbtBridge && managed then
        val jar = ZincLmUtil.fetchDefaultBridgeModule(
          sv,
          dependencyResolution.value,
          updateConfiguration.value,
          (update / unresolvedWarningConfiguration).value,
          streams.value.log
        )
        Some(jar)
      else None
    }
  }

  val myScalaCompilerBridgeBinaryJar: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    val cls = classOf[sbt.Keys.type]
    val isLegacy = Try(cls.getDeclaredMethod("scalaCompilerBridgeBinaryJar")).toOption.isDefined
    if isLegacy then oldScalaCompilerBridgeBinaryJarImpl
    else {
      // This task must be defined inline, otherwise it will result in NoSuchMethodFound on sbt 2 versions before 2.0.0-RC7.
      Def.task {
        val binaries = Keys.scalaCompilerBridgeBin.value
        val converter = Keys.fileConverter.value
        val files = binaries.map(converter.toPath).map(_.toFile)
        files.headOption
      }
    }
  }

end PluginCompat
