package org.jetbrains.sbt

import java.io.{PrintWriter, File}

import difflib._
import org.specs2.matcher.{MatchResult, XmlMatchers}
import org.specs2.mutable._
import Utilities._

import scala.xml._

class ImportSpec extends Specification with XmlMatchers {

  val testDataRoot = new File("src/test/data/" + TestCompat.sbtVersion)
  val androidHome = Option(System.getenv.get("ANDROID_HOME"))

  def testProject(project: String,  download: Boolean = true, sbtVersion: String = TestCompat.sbtVersionFull) = {

    val base = new File(testDataRoot, project)

    val expected = {
      val fs = new FS(new File(System.getProperty("user.home")), base)
      val testDataFile = new File(base, "structure-" + TestCompat.sbtVersionFull + ".xml")
      if (!testDataFile.exists())
        failure("No test data for version " + TestCompat.sbtVersionFull + " found!")
      val text = read(testDataFile).mkString("\n")
      val androidHome = this.androidHome getOrElse ""
      text
        .replace("$BASE", FS.toPath(base))
        .replace("$ANDROID_HOME", androidHome)
        .replace("$SHORTBASE", FS.toRichFile(base)(fs).path)
    }

    val actual = Loader.load(base, download, sbtVersion, verbose = true).mkString("\n")

    val a = XML.loadString(actual)
    val e = XML.loadString(expected)

    def onFail = {
      import scala.collection.JavaConversions._

      val act = new PrintWriter(new File(base, "actual.xml"))
      act.write(actual)
      act.close()
      val  diff = DiffUtils.diff(expected.lines.toList, actual.lines.toList)
      println("DIFF: " + project)
      diff.getDeltas foreach {d => println(d.getOriginal + "\n" + d.getRevised + "\n") }
      "Failed for project: " + project
    }

    a must beEqualToIgnoringSpace(e).updateMessage(_ => onFail)
  }

  def sbt13only = TestCompat.sbtVersion must be_==("0.13").orSkip("this test is for 0.13 only")

  def hasAndroidDefined = androidHome must beSome.orSkip("Android SDK home not defined")

  def t(s: String) = s +  " [" + TestCompat.sbtVersionFull+ "]"

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
      hasAndroidDefined and testProject("android")
    }

    t("be same in ide-settings project") in {
      sbt13only and testProject("ide-settings")
    }

    t("be same in sbt-idea project") in {
      sbt13only and testProject("sbt-idea")
    }
  }
}
