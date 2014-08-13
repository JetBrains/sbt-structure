package org.jetbrains.sbt

import sbt._


object Android {
  def extractAndroid(structure: BuildStructure, projectRef: ProjectRef): Option[AndroidData] = {
    val plugins = Seq(new AndroidSdkPlugin(structure, projectRef))
    for (plugin <- plugins) {
      val androidData = plugin.extractAndroid
      if (androidData.isDefined)
        return androidData
    }
    None
  }
}

abstract class AndroidSupportPlugin(val structure: BuildStructure, val projectRef: ProjectRef) {
  def extractAndroid: Option[AndroidData]
}

class AndroidSdkPlugin(structure: BuildStructure, projectRef: ProjectRef)
    extends AndroidSupportPlugin(structure, projectRef) {

  object Keys {
    val Android = config("android")

    val targetSdkVersion = SettingKey[String]("target-sdk-version")
    val manifestPath     = SettingKey[File]("manifest-path")
    val apkFile          = SettingKey[File]("apk-file")
    val libraryProject   = SettingKey[Boolean]("library-project")

    trait ProjectLayout {
      def res: File
      def assets: File
      def gen: File
      def libs: File
    }

    object ProjectLayout {
      def apply(base: File) = {
        if ((base / "src" / "main" / "AndroidManifest.xml").isFile)
          ProjectLayout.Gradle(base)
        else
          ProjectLayout.Ant(base)
      }
      case class Ant(base: File) extends ProjectLayout {
        override def res    = base / "res"
        override def assets = base / "assets"
        override def gen    = base / "gen"
        override def libs   = base / "libs"
      }
      case class Gradle(base: File) extends ProjectLayout {
                 def sources = base / "src" / "main"
        override def res     = sources / "res"
        override def assets  = sources / "assets"
        override def gen     = base / "target" / "android-gen"
        override def libs    = sources / "libs"
      }
    }
  }

  def extractAndroid: Option[AndroidData] = {
    def extractPath(from: SettingKey[java.io.File]): Option[String] =
      from.in(projectRef, Keys.Android).get(structure.data).map{_.getPath}

    def extract[T](from: SettingKey[T]): Option[T] =
      from.in(projectRef, Keys.Android).get(structure.data)

    val layout = Keys.ProjectLayout(new File(projectRef.build))

    for {
      targetVersion <- extract(Keys.targetSdkVersion)
      manifestPath  <- extractPath(Keys.manifestPath)
      apkPath       <- extractPath(Keys.apkFile)
      resPath       =  layout.res.getPath
      assetsPath    =  layout.assets.getPath
      genPath       =  layout.gen.getPath
      libsPath      =  layout.libs.getPath
      isLibrary     <- extract(Keys.libraryProject)
    } yield AndroidData(targetVersion, manifestPath, apkPath,
                          resPath, assetsPath, genPath, libsPath, isLibrary)
  }
}
