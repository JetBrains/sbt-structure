package org.jetbrains.sbt

import java.io.{File, PrintWriter}

import difflib._
import org.specs2.matcher.XmlMatchers
import org.specs2.mutable._

import scala.xml._

import XmlSerializer._

class ImportSpec extends Specification with XmlMatchers {

  val testDataRoot = new File("extractor/src/test/data/" + BuildInfo.sbtVersion)
  val androidHome = Option(System.getenv.get("ANDROID_HOME"))

  def testProject(project: String,  download: Boolean = true, sbtVersion: String = BuildInfo.sbtVersionFull) = {

    val base = new File(testDataRoot, project)

    val expected = {
      val testDataFile = new File(base, "structure-" + BuildInfo.sbtVersionFull + ".xml")
      if (!testDataFile.exists())
        failure("No test data for version " + BuildInfo.sbtVersionFull + " found!")
      val text = read(testDataFile).mkString("\n")
      val androidHome = this.androidHome getOrElse ""
      text
        .replace("$BASE", base.getCanonicalPath)
        .replace("$ANDROID_HOME", androidHome)
        .replace("~/", System.getProperty("user.home") + "/")
    }

    val actual = Loader.load(base, download, sbtVersion, verbose = true).mkString("\n")

    val a = XML.loadString(actual)

    // FIXME: this is a hack used for properly rewriting of paths
    //        only works for single project builds and thus should be replaced
    val pwd = System.getProperty("user.dir")
    System.setProperty("user.dir", base.getCanonicalPath)
    val e = XML.loadString(expected).deserialize[StructureData].right.map(_.serialize).right.get
    System.setProperty("user.dir", pwd)

    def onFail = {
      import scala.collection.JavaConversions._

      val act = new PrintWriter(new File(base, "actual.xml"))
      act.write(actual)
      act.close()
      val diff = DiffUtils.diff(actual.lines.toList, expected.lines.toList)
      println("DIFF: " + project)
      diff.getDeltas foreach {d => println(d.getOriginal + "\n" + d.getRevised + "\n") }
      "Failed for project: " + project
    }

    a must beEqualToIgnoringSpace(e).updateMessage(_ => onFail)
  }

  def sbt13only = BuildInfo.sbtVersion must be_==("0.13").orSkip("This test is for SBT 0.13 only")

  def onlyFor(version: String) = BuildInfo.sbtVersionFull must be_==(version).orSkip("This test if for SBT " + version + " only")

  def hasAndroidDefined = androidHome must beSome.orSkip("ANDROID_HOME is not defined")

  def t(s: String) = s +  " [" + BuildInfo.sbtVersionFull+ "]"

  "Imported xml" should {

    sequential // running 10 sbt instances at once is a bad idea unless you have >16G of ram

    t("be same in bare projects") in {
      testProject("bare")
    }

    t("be same in multiple projects") in {
      testProject("multiple")
    }

    t("be same in simple project") in {
      testProject("simple")
    }

    t("be same in managed dependency") in {
      testProject("dependency")
    }

    t("be same in multiple projects with classified deps") in {
      sbt13only and testProject("classifiers")
    }

    t("be same in project with optional dependency in") in {
      sbt13only and testProject("optional")
    }

    t("be same in play project") in {
      sbt13only and testProject("play", download = false, sbtVersion = "0.13.5")
    }

    t("be same in android project") in {
      sbt13only and (hasAndroidDefined and testProject("android"))
    }

    t("be same in ide-settings project") in {
      onlyFor("0.13.7") and testProject("ide-settings")
    }

    t("be same in sbt-idea project") in {
      sbt13only and testProject("sbt-idea")
    }
  }
}
