package org.jetbrains.sbt

import sbt._


object Android {
  def extractAndroid(structure: BuildStructure, projectRef: ProjectRef,
        keys: Seq[Def.ScopedKey[_]]): Option[AndroidData] = {
    val plugins = Seq(new AndroidSdkPlugin(structure, projectRef, keys))
    for (plugin <- plugins) {
      val androidData = plugin.extractAndroid
      if (androidData.isDefined)
        return androidData
    }
    None
  }
}

abstract class AndroidSupportPlugin(val structure: BuildStructure,
    val projectRef: ProjectRef, val keys: Seq[Def.ScopedKey[_]]) {
  def extractAndroid: Option[AndroidData]
}

class AndroidSdkPlugin(structure: BuildStructure, projectRef: ProjectRef,
    keys: Seq[Def.ScopedKey[_]]) extends AndroidSupportPlugin(structure, projectRef, keys) {

  object Keys {
    val Android = config("android")

    def inAndroidScope(key: Def.ScopedKey[_]) = key.scope.config match {
      case Select(k) => k.name == Android.name
      case _ => false
    }

    val targetSdkVersion = SettingKey[String]("target-sdk-version")
    val manifestPath     = SettingKey[File]("manifest-path")
    val apkFile          = SettingKey[File]("apk-file")
    val libraryProject   = SettingKey[Boolean]("library-project")
    val projectLayout    = keys.filter { k => k.key.label == "projectLayout" && inAndroidScope(k) }
                               .headOption.map { k => SettingKey(k.key).in(k.scope) }
  }

  def extractAndroid: Option[AndroidData] = {
    def extractPath(from: SettingKey[java.io.File]): Option[String] =
      from.in(projectRef, Keys.Android).get(structure.data).map{_.getPath}

    def extract[T](from: SettingKey[T]): Option[T] =
      from.in(projectRef, Keys.Android).get(structure.data)

    try {
      for {
        targetVersion <- extract(Keys.targetSdkVersion)
        manifestPath  <- extractPath(Keys.manifestPath)
        apkPath       <- extractPath(Keys.apkFile)
        projectLayout <- Keys.projectLayout
        optionLayout  <- extract(projectLayout)
        layout = optionLayout.asInstanceOf[{
          def res(): File; def assets(): File; def gen(): File; def libs(): File
        }]
        resPath    =  layout.res.getPath
        assetsPath =  layout.assets.getPath
        genPath    =  layout.gen.getPath
        libsPath   =  layout.libs.getPath
        isLibrary  <- extract(Keys.libraryProject)
      } yield AndroidData(targetVersion, manifestPath, apkPath,
                            resPath, assetsPath, genPath, libsPath, isLibrary)
    } catch {
      case _:java.lang.NoSuchMethodException => None
    }
  }
}
