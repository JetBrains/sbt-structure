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

    val expectedStr = {
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

    val actualStr = Loader.load(base, download, sbtVersion, verbose = true).mkString("\n")

    val actualXml = XML.loadString(actualStr)
    val expectedXml = XML.loadString(expectedStr)
    val actual = actualXml.deserialize[StructureData].right.get
    val expected = expectedXml.deserialize[StructureData].right.get

    def onXmlFail = {
      import scala.collection.JavaConversions._

      val act = new PrintWriter(new File(base, "actual.xml"))
      act.write(actualStr)
      act.close()
      val diff = DiffUtils.diff(actualStr.lines.toList, expectedStr.lines.toList)
      println("DIFF: " + project)
      diff.getDeltas foreach {d => println(d.getOriginal + "\n" + d.getRevised + "\n") }
      "Failed for project: " + project
    }

    def onEqualsFail = {
      val act = new PrintWriter(new File(base, "actual.txt"))
      act.write(prettyPrintCaseClass(actual))
      act.close()
      val exp = new PrintWriter(new File(base, "expected.txt"))
      exp.write(prettyPrintCaseClass(expected))
      exp.close()
      "Failed for project: " + project
    }

    actualXml must beEqualToIgnoringSpace(expectedXml).updateMessage(_ => onXmlFail)
    actual must beEqualTo(expected).updateMessage(_ => onEqualsFail)
  }

  def prettyPrintCaseClass(toPrint: Product): String = {
    val step = "  "
    def print0(what: Any, indent: String): String = what match {
      case p : Product =>
        if (p.productArity == 0) {
          indent + p.productPrefix
        } else {
          indent + p.productPrefix + ":\n" +
            p.productIterator.map {
              case s : Seq[_] => s.map(x => print0(x, indent + step)).mkString("\n")
              case pp : Product => print0(pp, indent + step)
              case other => indent + step + other.toString
            }.mkString("\n")
        }
      case other => indent + other.toString
    }

    print0(toPrint, step)
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
