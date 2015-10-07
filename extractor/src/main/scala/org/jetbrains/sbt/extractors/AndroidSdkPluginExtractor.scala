package org.jetbrains.sbt
package extractors

//import scala.language.reflectiveCalls

import org.jetbrains.sbt.structure.AndroidData
import sbt.Keys._
import sbt._


class AndroidSdkPluginExtractor(implicit projectRef: ProjectRef) extends Extractor {

  import AndroidSdkPluginExtractor._

  def extract(implicit state: State, options: Options): Option[AndroidData] = {
    val keys = state.attributes.get(sessionSettings) match {
      case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
      case _ => Seq.empty
    }

    try {
      for {
        targetVersion   <- projectTask(Keys.targetSdkVersion) orElse projectSetting(Keys.targetSdkVersion_1_3)
        manifestPath    <- projectSetting(Keys.manifestPath).map(_.getPath)
        apkPath         <- projectSetting(Keys.apkFile).map(_.getPath)
        isLibrary       <- projectSetting(Keys.libraryProject)
        proguardConfig  <- projectTask(Keys.proguardConfig)
        proguardOptions <- projectTask(Keys.proguardOptions)
        layoutAsAny     <- keys.findSettingKey("projectLayout").flatMap(projectSetting(_))
      } yield {
        val layout = layoutAsAny.asInstanceOf[ProjectLayout]
        AndroidData(targetVersion, manifestPath, apkPath,
          layout.res.getPath, layout.assets.getPath, layout.gen.getPath, layout.libs.getPath,
          isLibrary, proguardConfig ++ proguardOptions)
      }
    } catch {
      case _ : NoSuchMethodException => None
    }
  }
}

object AndroidSdkPluginExtractor {
  def apply(implicit state: State, projectRef: ProjectRef, options: Options): Option[AndroidData] =
    new AndroidSdkPluginExtractor().extract

  private val Android = config("android")

  private def isInAndroidScope(key: ScopedKey[_]) = key.scope.config match {
    case Select(k) => k.name == Android.name
    case _ => false
  }

  implicit def toRichSeqWithScopedKey(keys: Seq[sbt.ScopedKey[_]]) = new {
    def findSettingKey(label: String): Option[SettingKey[_]] =
      keys.find(k => k.key.label == label && isInAndroidScope(k))
        .map(k => SettingKey(k.key).in(k.scope))

    def findTaskKey(label: String): Option[TaskKey[_]] =
      keys.find(k => k.key.label == label && isInAndroidScope(k))
        .map(k => TaskKey(k.key.asInstanceOf[AttributeKey[Task[Any]]]).in(k.scope))
  }

  private object Keys {
    val targetSdkVersion = TaskKey[String]("target-sdk-version").in(Android)
    val targetSdkVersion_1_3 = SettingKey[String]("target-sdk-version").in(Android)
    val manifestPath = SettingKey[File]("manifest-path").in(Android)
    val apkFile = SettingKey[File]("apk-file").in(Android)
    val libraryProject = SettingKey[Boolean]("library-project").in(Android)
    val proguardConfig = TaskKey[Seq[String]]("proguard-config").in(Android)
    val proguardOptions = TaskKey[Seq[String]]("proguard-options").in(Android)
  }

  private type ProjectLayout = { def res: File; def assets: File; def gen: File; def libs: File }
}
