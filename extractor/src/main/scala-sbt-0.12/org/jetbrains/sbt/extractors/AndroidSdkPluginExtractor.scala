package org.jetbrains.sbt
package extractors

//import scala.language.reflectiveCalls

import java.io.File

import org.jetbrains.sbt.structure.{Aar, AndroidData, ApkLib, BuildData, ConfigurationData, DependencyData, DirectoryData, ProjectData}
import sbt.Project.Initialize
import sbt._


object AndroidSdkPluginExtractor extends SbtStateOps with TaskOps {

  def taskDef: Initialize[Task[Option[AndroidData]]] =
    (sbt.Keys.state, sbt.Keys.thisProjectRef) flatMap { (state, projectRef) =>
      val keys = state.attributes.get(sbt.Keys.sessionSettings) match {
        case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
        case _ => Seq.empty
      }

      val manifestFileTaskOpt = Keys.processManifest.in(projectRef).find(state)
        .orElse(Keys.manifestPath.in(projectRef).find(state).map(_.toTask))
      val layoutAsAnyOpt = findSettingKeyIn(keys, "projectLayout")
        .flatMap(_.in(projectRef).find(state))
      val apklibsAsAnyTaskOpt = findTaskKeyIn(keys, "apklibs")
        .flatMap(_.in(projectRef).find(state))
      val aarsAsAnyTaskOpt = findTaskKeyIn(keys, "aars")
        .flatMap(_.in(projectRef).find(state))

      val androidTaskOpt = for {
        manifestTask        <- manifestFileTaskOpt
        apk                 <- Keys.apkFile.in(projectRef).find(state)
        isLibrary           <- Keys.libraryProject.in(projectRef).find(state)
        layoutAsAny         <- layoutAsAnyOpt
        apklibsAsAnyTask    <- apklibsAsAnyTaskOpt
        targetVersionTask   <- Keys.settingOrTask[String](Keys.targetSdkVersionKey, projectRef, state)
        aarsAsAnyTask       <- aarsAsAnyTaskOpt
        proguardConfigTask  <- Keys.proguardConfig.in(projectRef).find(state)
        proguardOptionsTask <- Keys.settingOrTask[Seq[String]](Keys.proguardOptionsKey, projectRef, state)
      } yield {
        for {
          manifest        <- manifestTask
          targetVersion   <- targetVersionTask
          proguardConfig  <- proguardConfigTask
          proguardOptions <- proguardOptionsTask
          apklibsAsAny    <- apklibsAsAnyTask
          aarsAsAny       <- aarsAsAnyTask
        } yield {
          try {
            val layout  = layoutAsAny.asInstanceOf[ProjectLayout]
            val apklibs = apklibsAsAny.asInstanceOf[Seq[LibraryDependency]]
            val aars    = aarsAsAny.asInstanceOf[Seq[LibraryDependency]].map(libraryDepToAar(targetVersion))
            Some(AndroidData(targetVersion, manifest, apk,
              layout.res, layout.assets, layout.gen, layout.libs,
              isLibrary, proguardConfig ++ proguardOptions,
              apklibs.map(libraryDepToApkLib), aars))
          } catch {
            case _ : NoSuchMethodException => None
          }
        }
      }

      androidTaskOpt.getOrElse(None.toTask)
    }

  private val Android = config("android")

  private object Keys {
    val targetSdkVersionKey = "target-sdk-version"
    val manifestPath = SettingKey[File]("manifest-path").in(Android)
    val processManifest = TaskKey[File]("process-manifest").in(Android)
    val apkFile = SettingKey[File]("apk-file").in(Android)
    val libraryProject = SettingKey[Boolean]("library-project").in(Android)
    val proguardConfig = TaskKey[Seq[String]]("proguard-config").in(Android)
    val proguardOptionsKey = "proguard-options"

    def settingOrTask[A : Manifest](key: String, projectRef: ProjectRef, state: State) = {
      TaskKey[A](key).in(Android).in(projectRef).find(state)
        .orElse(SettingKey[A](key).in(Android).in(projectRef).find(state).map(_.toTask))
    }
  }

  private type ProjectLayout = {
    def base: File
    def res: File
    def assets: File
    def gen: File
    def bin: File
    def libs: File
    def sources: File
    def resources: File
    def manifest: File
  }

  private type LibraryDependency = {
    def layout: ProjectLayout
    def getName: String
    def getJarFile: File
  }

  private def findSettingKeyIn(keys: Seq[sbt.ScopedKey[_]], label: String): Option[SettingKey[Any]] =
    keys.find(k => k.key.label == label && isInAndroidScope(k))
      .map(k => SettingKey(k.key.asInstanceOf[AttributeKey[Any]]).in(k.scope))

  private def findTaskKeyIn(keys: Seq[sbt.ScopedKey[_]], label: String): Option[TaskKey[Any]] =
    keys.find(k => k.key.label == label && isInAndroidScope(k))
      .map(k => TaskKey(k.key.asInstanceOf[AttributeKey[Task[Any]]]).in(k.scope))

  private def libraryDepToApkLib(lib: LibraryDependency): ApkLib = {
    // As for version 1.5.0 android-sdk-plugin uses canonical path to library as its name
    val fixedLibName = lib.getName.split(File.separatorChar).last
    ApkLib(fixedLibName, lib.layout.base, lib.layout.manifest, lib.layout.sources, lib.layout.res, lib.layout.libs, lib.layout.gen)
  }

  private def libraryDepToAar(targetSdkVersion: String)(lib: LibraryDependency): Aar = {
    val fixedLibName = lib.getName.split(File.separatorChar).last
    val android = AndroidData(targetSdkVersion, lib.layout.manifest, lib.layout.base,
      lib.layout.res, lib.layout.assets, lib.layout.gen, lib.layout.libs,
      true, Nil, Nil, Nil)
    val project = ProjectData(fixedLibName, fixedLibName, "sbt-android-synthetic-organization", "0.1-SNAPSHOT-sbt-android",
      lib.layout.base, Nil, lib.layout.bin, BuildData(Nil, Nil, Nil, Nil),
      ConfigurationData("compile", Seq(DirectoryData(lib.layout.sources, true)), Seq(DirectoryData(lib.layout.resources, true)), Nil, lib.getJarFile) :: Nil, None, None, Some(android),
      DependencyData(Nil, Nil, Nil), Set.empty, None)
    Aar(fixedLibName, project)
  }

  private def isInAndroidScope(key: ScopedKey[_]) = key.scope.config match {
    case Select(k) => k.name == Android.name
    case _ => false
  }
}
