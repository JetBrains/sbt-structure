package org.jetbrains.sbt
package extractors

//import scala.language.reflectiveCalls

import java.io.File

import org.jetbrains.sbt.structure.{ApkLib, AndroidData}
import sbt._
import sbt.Project.Initialize


object AndroidSdkPluginExtractor extends SbtStateOps with TaskOps {

  def taskDef: Initialize[Task[Option[AndroidData]]] =
    (sbt.Keys.state, sbt.Keys.thisProjectRef) flatMap { (state, projectRef) =>
      val keys = state.attributes.get(sbt.Keys.sessionSettings) match {
        case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
        case _ => Seq.empty
      }

      val targetVersionTaskOpt = Keys.targetSdkVersion.in(projectRef).find(state)
        .orElse(Keys.targetSdkVersion_1_3.in(projectRef).find(state).map(_.toTask))
      val layoutAsAnyOpt = findSettingKeyIn(keys, "projectLayout")
        .flatMap(_.in(projectRef).find(state))
      val apklibsAsAnyTaskOpt = findTaskKeyIn(keys, "apklibs")
        .flatMap(_.in(projectRef).find(state))

      val androidTaskOpt = for {
        manifest    <- Keys.manifestPath.in(projectRef).find(state)
        apk         <- Keys.apkFile.in(projectRef).find(state)
        isLibrary   <- Keys.libraryProject.in(projectRef).find(state)
        layoutAsAny <- layoutAsAnyOpt
        apklibsAsAnyTask    <- apklibsAsAnyTaskOpt
        targetVersionTask   <- targetVersionTaskOpt
        proguardConfigTask  <- Keys.proguardConfig.in(projectRef).find(state)
        proguardOptionsTask <- Keys.proguardOptions.in(projectRef).find(state)
      } yield {
        for {
          targetVersion   <- targetVersionTask
          proguardConfig  <- proguardConfigTask
          proguardOptions <- proguardOptionsTask
          apklibsAsAny    <- apklibsAsAnyTask
        } yield {
          try {
            val layout = layoutAsAny.asInstanceOf[ProjectLayout]
            val apklibs = apklibsAsAny.asInstanceOf[Seq[LibraryDependency]]
            Some(AndroidData(targetVersion, manifest, apk,
              layout.res, layout.assets, layout.gen, layout.libs,
              isLibrary, proguardConfig ++ proguardOptions,
              apklibs.map(libraryDepToApkLib)))
          } catch {
            case _ : NoSuchMethodException => None
          }
        }
      }

      androidTaskOpt.getOrElse(None.toTask)
    }

  private val Android = config("android")

  private object Keys {
    val targetSdkVersion = TaskKey[String]("target-sdk-version").in(Android)
    val targetSdkVersion_1_3 = SettingKey[String]("target-sdk-version").in(Android)
    val manifestPath = SettingKey[File]("manifest-path").in(Android)
    val apkFile = SettingKey[File]("apk-file").in(Android)
    val libraryProject = SettingKey[Boolean]("library-project").in(Android)
    val proguardConfig = TaskKey[Seq[String]]("proguard-config").in(Android)
    val proguardOptions = TaskKey[Seq[String]]("proguard-options").in(Android)
  }

  private type ProjectLayout = {
    def base: File
    def res: File
    def assets: File
    def gen: File
    def libs: File
    def sources: File
    def manifest: File
  }

  private type LibraryDependency = {
    def layout: ProjectLayout
    def getName: String
  }

  private def findSettingKeyIn(keys: Seq[sbt.ScopedKey[_]], label: String): Option[SettingKey[Any]] =
    keys.find(k => k.key.label == label && isInAndroidScope(k))
      .map(k => SettingKey(k.key.asInstanceOf[AttributeKey[Any]]).in(k.scope))

  private def findTaskKeyIn(keys: Seq[sbt.ScopedKey[_]], label: String): Option[TaskKey[Any]] =
    keys.find(k => k.key.label == label && isInAndroidScope(k))
      .map(k => TaskKey(k.key.asInstanceOf[AttributeKey[Task[Any]]]).in(k.scope))

  private def libraryDepToApkLib(lib: LibraryDependency): ApkLib = {
    // As for version 1.5.0 android-sdk-plugin uses canonical path to library as its name
    val fixedLibName = lib.getName.split(File.separator).last
    ApkLib(fixedLibName, lib.layout.base, lib.layout.manifest, lib.layout.sources, lib.layout.res, lib.layout.libs, lib.layout.gen)
  }

  private def isInAndroidScope(key: ScopedKey[_]) = key.scope.config match {
    case Select(k) => k.name == Android.name
    case _ => false
  }
}
