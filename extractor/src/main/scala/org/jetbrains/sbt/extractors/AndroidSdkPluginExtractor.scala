package org.jetbrains.sbt
package extractors

//import scala.language.reflectiveCalls

import java.io.File

import org.jetbrains.sbt.structure.{ApkLib, AndroidData}
import sbt.Keys._
import sbt._


class AndroidSdkPluginExtractor(implicit projectRef: ProjectRef) extends SbtStateOps {

  import AndroidSdkPluginExtractor._

  def extract(implicit state: State): Option[AndroidData] = {
    val keys = state.attributes.get(sessionSettings) match {
      case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
      case _ => Seq.empty
    }

    try {
      for {
        targetVersion   <- projectTask(Keys.targetSdkVersion) orElse projectSetting(Keys.targetSdkVersion_1_3)
        manifest        <- projectSetting(Keys.manifestPath)
        apk             <- projectSetting(Keys.apkFile)
        isLibrary       <- projectSetting(Keys.libraryProject)
        proguardConfig  <- projectTask(Keys.proguardConfig)
        proguardOptions <- projectTask(Keys.proguardOptions)
        layoutAsAny     <- findSettingKey(keys, "projectLayout").flatMap(projectSetting(_))
        apklibsAsAny    <- findTaskKey(keys, "apklibs").flatMap(projectTask(_))
      } yield {
        val layout = layoutAsAny.asInstanceOf[ProjectLayout]
        val apklibs = apklibsAsAny.asInstanceOf[Seq[LibraryDependency]]
        AndroidData(targetVersion, manifest, apk,
          layout.res, layout.assets, layout.gen, layout.libs,
          isLibrary, proguardConfig ++ proguardOptions, apklibs.map(libraryDepToApkLib))
      }
    } catch {
      case _ : NoSuchMethodException => None
    }
  }

  private def findSettingKey(keys: Seq[sbt.ScopedKey[_]], label: String): Option[SettingKey[_]] =
    keys.find(k => k.key.label == label && isInAndroidScope(k))
      .map(k => SettingKey(k.key).in(k.scope))

  private def findTaskKey(keys: Seq[sbt.ScopedKey[_]], label: String): Option[TaskKey[_]] =
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

object AndroidSdkPluginExtractor {
  def apply(implicit state: State, projectRef: ProjectRef): Option[AndroidData] =
    new AndroidSdkPluginExtractor().extract

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
}
