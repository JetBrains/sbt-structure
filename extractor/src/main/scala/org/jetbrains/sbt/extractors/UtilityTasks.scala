package org.jetbrains.sbt.extractors

import org.jetbrains.sbt._
import org.jetbrains.sbt.structure.ProjectData
import org.jetbrains.sbt.structure.XmlSerializer._
import sbt.Def.Initialize
import sbt.{Def, _}

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter}
import scala.collection.mutable.ArrayBuffer
// don't remove this import: sbt.jetbrains.apiAdapter._ -- it shadows some symbols for sbt 1.0 compatibility
import scala.language.reflectiveCalls

/**
 * @author Nikolay Obedin
 */
object UtilityTasks extends SbtStateOps {

  /*
    Selects Scala 2 in Scala 3 projects that contain Scala 2 in crossScalaVersions.
    https://youtrack.jetbrains.com/issue/SCL-19573

    The ++ command is per-build, but the crossScalaVersions key is per-project and values may vary.
    The built-in command affects projects that contain a compatible Scala 2 version, but not necessarily all projects that contain Scala 2 in crossScalaVersions.
    Besides, the built-in command switches to a compatible Scala version, even if the exact version is not listed in crossScalaVersions.
    To use listed Scala versions, per project, we have to re-implement the sbt.Cross.switchVersion command.
    It's not clear whether it's safe to select different Scala 2 versions in different projects.
    In practice, crossScalaVersions in different projects should be compatible.
  */
  lazy val preferScala2: Command = Command.command("preferScala2") { state =>
    val (structure, data) = {
      val extracted = Project.extract(state)
      (extracted.structure, extracted.structure.data)
    }

    val scala3Projects = structure.allProjectRefs.filter { project =>
      Keys.scalaVersion.in(project).get(data).exists(_.startsWith("3."))
    }

    val crossScala2VersionsInScala3Projects = scala3Projects.flatMap { project =>
      Keys.crossScalaVersions.in(project).get(data).getOrElse(Seq.empty).filter(_.startsWith("2."))
    }

    // We can only do this when all sbt projects cross-compile to Scala 2 & Scala 3
    // See https://youtrack.jetbrains.com/issue/SCL-22619/
    val canSetScala2VersionGlobally = crossScala2VersionsInScala3Projects.nonEmpty &&
      scala3Projects.length == crossScala2VersionsInScala3Projects.length
    if (canSetScala2VersionGlobally) {
      "++" + crossScala2VersionsInScala3Projects.maxBy(numbersOf) :: state
    } else {
      state
    }
  }

  private def numbersOf(version: String): (Int, Int, Int) = {
    val prefix = version.split('.').filter(s => s.nonEmpty && s.forall(_.isDigit)).map(_.toInt).take(3)
    val xs = prefix ++ Seq.fill(3 - prefix.length)(0)
    (xs(0), xs(1), xs(2))
  }

  lazy val dumpStructure: Initialize[Task[Unit]] = Def.task {
    val structure = StructureKeys.extractStructure.value
    val options = StructureKeys.sbtStructureOpts.value
    val outputFile = StructureKeys.sbtStructureOutputFile.value
    val log = Keys.streams.value.log

    val outputText = {
      if (options.prettyPrint)
        newXmlPrettyPrinter.format(structure.serialize)
      else
        xml.Utility.trim(structure.serialize).mkString
    }

    outputFile.map { file =>
      log.info("Writing structure to " + file.getPath + "...")
      // noinspection UnitInMap
      writeToFile(file, outputText)
    } getOrElse {
      log.info("Writing structure to console:")
      println(outputText)
    }
    log.info("Done.")
  }

  lazy val localCachePath: Def.Initialize[Task[Option[File]]] = Def.task {
    Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home"))).map(file)
  }

  lazy val acceptedProjects: Initialize[Task[Seq[ProjectRef]]] = Keys.state.map { state =>
    structure(state).allProjectRefs.filter { case ref@ProjectRef(_, id) =>
      val isProjectAccepted = structure(state).allProjects.find(_.id == id).exists(areNecessaryPluginsLoaded)
      val shouldSkipProject =
        SettingKeys.ideSkipProject.in(ref).getOrElse(state, false) ||
          SettingKeys.sbtIdeaIgnoreModule.in(ref).getOrElse(state, false)
      isProjectAccepted && !shouldSkipProject
    }
  }

  /**
    * The build needs to be extracted for only one project per build, as it is shared among subprojects.
    */
  lazy val extractBuilds = Def.taskDyn {
    val state = Keys.state.value

    val buildProjects = StructureKeys.acceptedProjects.value
      .groupBy(_.build)
      .values
      .map(_.head)
      .toSeq

    Def.task {
      StructureKeys.extractBuild
        .forAllProjects(state, buildProjects)
        .map(_.values.toSeq)
        .value
    }
  }

  lazy val extractProjects: Def.Initialize[Task[Seq[ProjectData]]] = Def.taskDyn {
    val state = Keys.state.value
    val accepted = StructureKeys.acceptedProjects.value

    Def.task {
      StructureKeys.extractProject
        .forAllProjects(state, accepted)
        .map(_.values.toSeq)
        .value
    }
  }

  def allConfigurationsWithSource: Def.Initialize[Seq[Configuration]] = Def.settingDyn {
    val cs = for {
      c <- Keys.ivyConfigurations.value
    } yield (Keys.sourceDirectories in c).?.apply { filesOpt => filesOpt.flatMap(f => f.nonEmpty.option(c))}

    cs.foldLeft(Def.setting(Seq.empty[Configuration])) { (accDef, initOptConf) =>
      accDef.zipWith(initOptConf) {(acc, optConf) => acc ++ optConf.toSeq }
    }
  }

  def testConfigurations: Def.Initialize[Seq[Configuration]] =
    StructureKeys.allConfigurationsWithSource.apply { cs =>
      import sbt._
      val predefinedTest = Set(Test, IntegrationTest)
      val transitiveTest = cs.filter(c =>
        transitiveExtends(c.extendsConfigs)
          .toSet
          .intersect(predefinedTest).nonEmpty
      )
      // note: IntegrationTest is not a predefined configuration in each sbt project. It has to be manually enabled.
      // So returning it from testConfigurations is not necessary and it causes incorrect values to be returned from the sourceDirectory key.
      val predefinedAvailableTest = predefinedTest.filter(cs.contains).toSeq
      (transitiveTest ++ predefinedAvailableTest).distinct
    }

  def sourceConfigurations: Def.Initialize[Seq[Configuration]] = Def.setting {
    import sbt._
    (allConfigurationsWithSource.value.diff(StructureKeys.testConfigurations.value) ++ Seq(Compile)).distinct
  }

  def dependencyConfigurations: Def.Initialize[Seq[Configuration]] = {
    import sbt._
    allConfigurationsWithSource.apply(cs => (cs ++ Seq(Runtime, Provided, Optional)).distinct)
  }

  def librariesClassifiers(options: Options): Seq[String] = {
    val buffer = new ArrayBuffer[String]
    if (options.resolveSourceClassifiers)
      buffer += Artifact.SourceClassifier
    if (options.resolveJavadocClassifiers)
      buffer += Artifact.DocClassifier
    buffer
  }

  private def areNecessaryPluginsLoaded(project: ResolvedProject): Boolean = {
    // Here is a hackish way to test whether project has JvmPlugin enabled.
    // Prior to 0.13.8 SBT had this one enabled by default for all projects.
    // Now there may exist projects with IvyPlugin (and thus JvmPlugin) disabled
    // lacking all the settings we need to extract in order to import project in IDEA.
    // These projects are filtered out by checking `autoPlugins` field.
    // But earlier versions of SBT 0.13.x had no `autoPlugins` field so
    // structural typing is used to get the data.
    try {
      type ResolvedProject_0_13_7 = {def autoPlugins: Seq[{ def label: String}]}
      val resolvedProject_0_13_7 = project.asInstanceOf[ResolvedProject_0_13_7]
      val labels = resolvedProject_0_13_7.autoPlugins.map(_.label)
      labels.contains("sbt.plugins.JvmPlugin")
    } catch {
      case _ : NoSuchMethodException => true
    }
  }

  def writeToFile(file: File, xml: String): Unit = {
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))
    try {
      writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
      writer.newLine()
      writer.write(xml)
      writer.flush()
    } finally {
      writer.close()
    }
  }
}
