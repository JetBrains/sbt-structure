package org.jetbrains.sbt

import sbt._


object Android {
  def extractAndroid(structure: BuildStructure, projectRef: ProjectRef): Option[AndroidData] = {
    val plugins = Seq(new AndroidSdkPlugin(structure, projectRef))
    for (plugin <- plugins) {
      try {
        val androidData = plugin.extractAndroid
        if (androidData.isDefined)
          return androidData
      } catch {
        case _: java.lang.NoClassDefFoundError => // do nothing
      }
    }
    None
  }
}

abstract class AndroidSupportPlugin(val structure: BuildStructure, val projectRef: ProjectRef) {
  def extractAndroid: Option[AndroidData]
}

class AndroidSdkPlugin(structure: BuildStructure, projectRef: ProjectRef)
    extends AndroidSupportPlugin(structure, projectRef) {

  import android.Keys

  def extractAndroid: Option[AndroidData] = {
    def isAndroidProject =
      Keys.manifestPath.in(projectRef, Keys.Android).get(structure.data).map{_.isFile}.getOrElse(false)

    if (!isAndroidProject) return None

    def extractPath(from: SettingKey[java.io.File]): String =
      from.in(projectRef, Keys.Android).get(structure.data).map{_.getPath}.get

    def extract[T](from: SettingKey[T]): Option[T] =
      from.in(projectRef, Keys.Android).get(structure.data)

    val layout = extract(Keys.projectLayout) getOrElse Keys.ProjectLayout(new File(projectRef.build))

    val targetVersion = extract(Keys.targetSdkVersion).get
    val manifestPath  = extractPath(Keys.manifestPath)
    val apkPath       = extractPath(Keys.apkFile)
    val resPath       = layout.res.getPath
    val assetsPath    = layout.assets.getPath
    val genPath       = layout.gen.getPath
    val libsPath      = layout.libs.getPath
    val isLibrary     = extract(Keys.libraryProject).get

    Some(AndroidData(targetVersion, manifestPath, apkPath,
                     resPath, assetsPath, genPath, libsPath, isLibrary))
  }
}
