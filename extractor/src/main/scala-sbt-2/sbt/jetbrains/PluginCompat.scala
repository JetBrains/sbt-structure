package sbt.jetbrains

import org.jetbrains.sbt.compat.FileConverterCompat
import sbt.*
import scala.util.Try

object PluginCompat extends SeqOpsCompat with ClasspathOpsCompat with CoursierLoggerSettingsCompat:

  type SbtSettings = Settings

  def extractProject(state: sbt.State): sbt.Extracted = {
    import ProjectExtra.extract
    sbt.Project.extract(state)
  }

  def isTaskOrInputTask(attributeKey: AttributeKey[_]): Boolean =
    attributeKey.tag.isTaskOrInputTask

  /**
   * In sbt 2, `scalacOptions` can contain paths with placeholders instead of absolute ones, e.g.
   * `-Xplugin:${CSR_CACHE}/.../better-monadic-for.jar`. Returns a resolver that replaces these
   * placeholders with real paths using sbt's `rootPaths` map, the same way sbt 2 does it in
   * `Compiler.resolveVirtualizedScalacOptions`.
   *
   * @see [[https://youtrack.jetbrains.com/issue/SCL-25761]]
   * @see [[https://github.com/sbt/sbt/blob/de1b5ee9eaff469f3b0e11d20d7fed31a13ba4e1/main/src/main/scala/sbt/internal/Compiler.scala#L462]]
   */
  def scalacOptionsResolver: Def.Initialize[Task[Seq[String] => Seq[String]]] =
    Def.task {
      val rootPaths = Keys.rootPaths.value

      def convertValue(value: String): String =
        rootPaths.find((key, _) => value.startsWith(s"$${$key}/")) match
          case Some((key, p)) => p.resolve(value.stripPrefix(s"$${$key}/")).toString()
          case None           => value

      (options: Seq[String]) =>
        options.map { option =>
          // Mirrors zinc `resolveVirtualizedScalacOptions`, which skips the split/rejoin when there is no `$`.
          // https://github.com/sbt/zinc/blob/0f7893d80893ae93dd745769dfbee351d29d2480/zinc/src/main/scala/sbt/internal/inc/MixedAnalyzingCompiler.scala#L162
          if (!option.contains("$")) option
          else option.split(":").map(_.split(",").map(convertValue).mkString(",")).mkString(":")
        }
    }

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
    Keys.excludeLintKeys ++= Set(
      Keys.updateClassifiers / Keys.transitiveClassifiers,
      Keys.updateSbtClassifiers / Keys.transitiveClassifiers,
    ),
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

  def fileConverterCompat: Def.Initialize[FileConverterCompat] = Def.setting {
    FileConverterCompat(Keys.fileConverter.value)
  }

end PluginCompat
