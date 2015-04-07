package org.jetbrains.sbt

//import scala.language.reflectiveCalls

import sbt._
import sbt.Keys._


object Android {
  def extractAndroid(structure: sbt.Load.BuildStructure, projectRef: ProjectRef,
      state: State): Option[AndroidData] = {
    val plugins = Seq(new AndroidSdkPlugin(structure, projectRef, state))
    for (plugin <- plugins) {
      val androidData = plugin.extractAndroid
      if (androidData.isDefined)
        return androidData
    }
    None
  }
}

abstract class AndroidSupportPlugin(val structure: sbt.Load.BuildStructure,
    val projectRef: ProjectRef, val state: State) {
  def extractAndroid: Option[AndroidData]
}

class AndroidSdkPlugin(structure: sbt.Load.BuildStructure, projectRef: ProjectRef,
    state: State) extends AndroidSupportPlugin(structure, projectRef, state) {

  val keys = state.attributes.get(sessionSettings) match {
    case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
    case _ => Seq.empty
  }

  object Keys {
    val Android = config("android")

    def inAndroidScope(key: ScopedKey[_]) = key.scope.config match {
      case Select(k) => k.name == Android.name
      case _ => false
    }

    val targetSdkVersion = SettingKey[String]("target-sdk-version")
    val manifestPath     = SettingKey[File]("manifest-path")
    val apkFile          = SettingKey[File]("apk-file")
    val libraryProject   = SettingKey[Boolean]("library-project")
    val proguardConfig   = TaskKey[Seq[String]]("proguard-config")
    val proguardOptions  = TaskKey[Seq[String]]("proguard-options")
    val projectLayout    = keys.find { k => k.key.label == "projectLayout" && inAndroidScope(k) }
                               .map { k => SettingKey(k.key).in(k.scope) }

    type ProjectLayout = { def res: File; def assets: File; def gen: File; def libs: File }
  }

  def extractAndroid: Option[AndroidData] = {
    def extractPath(from: SettingKey[java.io.File]): Option[String] =
      from.in(projectRef, Keys.Android).get(structure.data).map{_.getPath}

    def extract[T](from: SettingKey[T]): Option[T] =
      from.in(projectRef, Keys.Android).get(structure.data)

    def extractTask[T](from: TaskKey[T]): Option[T] =
      Project.runTask(from.in(projectRef, Keys.Android), state).collect {
        case (_, Value(result)) => result
      }

    try {
      for {
        targetVersion <- extract(Keys.targetSdkVersion)
        manifestPath  <- extractPath(Keys.manifestPath)
        apkPath       <- extractPath(Keys.apkFile)
        projectLayout <- Keys.projectLayout
        optionLayout  <- extract(projectLayout)
        layout     = optionLayout.asInstanceOf[Keys.ProjectLayout]
        resPath    =  layout.res.getPath
        assetsPath =  layout.assets.getPath
        genPath    =  layout.gen.getPath
        libsPath   =  layout.libs.getPath
        isLibrary  <- extract(Keys.libraryProject)
        proguardConfig <- extractTask(Keys.proguardConfig)
        proguardOptions <- extractTask(Keys.proguardOptions)
      } yield AndroidData(targetVersion, manifestPath, apkPath,
                            resPath, assetsPath, genPath, libsPath, isLibrary,
                            proguardConfig ++ proguardOptions)
    } catch {
      case _ : NoSuchMethodException => None
    }
  }
}
