package org.jetbrains.sbt.extractors

//import scala.language.reflectiveCalls

import org.jetbrains.sbt.structure.AndroidData
import org.jetbrains.sbt.extractors.Extractor.Options
import sbt.Keys._
import sbt._


class AndroidSdkPluginExtractor(projectRef: ProjectRef) extends Extractor {
  override type Data = AndroidData

  implicit val projectRefImplicit = projectRef

  override def extract(implicit state: State, options: Options): Option[Data] = {
    val keys = state.attributes.get(sessionSettings) match {
      case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
      case _ => Seq.empty
    }

    object AndroidKeys {
      val Android = config("android")

      def isInAndroidScope(key: ScopedKey[_]) = key.scope.config match {
        case Select(k) => k.name == Android.name
        case _ => false
      }

      val targetSdkVersion     = TaskKey[String]("target-sdk-version").in(Android)
      val targetSdkVersion_1_3 = SettingKey[String]("target-sdk-version").in(Android)
      val manifestPath         = SettingKey[File]("manifest-path").in(Android)
      val apkFile              = SettingKey[File]("apk-file").in(Android)
      val libraryProject       = SettingKey[Boolean]("library-project").in(Android)
      val proguardConfig       = TaskKey[Seq[String]]("proguard-config").in(Android)
      val proguardOptions      = TaskKey[Seq[String]]("proguard-options").in(Android)

      val projectLayout =
        keys.find(k => k.key.label == "projectLayout" && isInAndroidScope(k))
            .map(k => SettingKey(k.key).in(k.scope))

      type ProjectLayout = { def res: File; def assets: File; def gen: File; def libs: File }
    }

    try {
      for {
        targetVersion   <- projectTask(AndroidKeys.targetSdkVersion) orElse projectSetting(AndroidKeys.targetSdkVersion_1_3)
        manifestPath    <- projectSetting(AndroidKeys.manifestPath).map(_.getPath)
        apkPath         <- projectSetting(AndroidKeys.apkFile).map(_.getPath)
        isLibrary       <- projectSetting(AndroidKeys.libraryProject)
        proguardConfig  <- projectTask(AndroidKeys.proguardConfig)
        proguardOptions <- projectTask(AndroidKeys.proguardOptions)
        optionLayout    <- AndroidKeys.projectLayout.flatMap(k => projectSetting(k))
      } yield {
        val layout = optionLayout.asInstanceOf[AndroidKeys.ProjectLayout]
        AndroidData(targetVersion, manifestPath, apkPath,
          layout.res.getPath, layout.assets.getPath, layout.gen.getPath, layout.libs.getPath,
          isLibrary, proguardConfig ++ proguardOptions)
      }
    } catch {
      case _ : NoSuchMethodException => None
    }
  }
}
