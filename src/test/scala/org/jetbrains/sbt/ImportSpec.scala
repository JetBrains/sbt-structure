package org.jetbrains.sbt

import java.io.{PrintWriter, File}

import org.specs2.matcher.XmlMatchers
import org.specs2.mutable._
import scala.xml._

class ImportSpec extends Specification with XmlMatchers {

  val testDataRoot = new File("src/test/data/" + TestCompat.sbtVersion)
  val androidHome = Option(System.getenv.get("ANDROID_HOME"))

  def testProject(project: String,  download: Boolean = true, sbtVersion: String = TestCompat.sbtVersionFull) = {

    val base = new File(testDataRoot, project)
    val actual = Loader.load(base, download, sbtVersion).mkString("\n")

    val expected = {
      val fs = new FS(new File(System.getProperty("user.home")))
      val text = read(new File(base, "structure.xml")).mkString("\n")
      val androidHome = this.androidHome getOrElse ""
      text.replace("$BASE", FS.toPath(base)).replace("$ANDROID_HOME", androidHome).replace("$SHORTBASE", FS.toRichFile(base)(fs).path)
    }

    val a = XML.loadString(actual)
    val e = XML.loadString(expected)

    def onFail = {
      val asd = new PrintWriter(new File(base, "actual.xml"))
      asd.write(actual)
      asd.close()
      "Failed for project: " + project + "\nSee actual.xml"
    }

    a must beEqualToIgnoringSpace(e).updateMessage(_ => onFail)
  }

  def sbt13only = TestCompat.sbtVersion must be_==("0.13").orSkip("this test is for 0.13 only")

  def hasAndroidDefined = androidHome must beSome.orSkip("Android SDK home not defined")

  "Imported xml" should {

    sequential // running 10 sbt instances at once is a bad idea unless you have >16G of ram

    "be same in bare projects" in {
      testProject("bare")
    }

    "be same in multiple projects" in {
      testProject("multiple")
    }

    "be same in simple project" in {
      testProject("simple")
    }

    "be same in managed dependency" in {
      testProject("dependency")
    }

    "be same in multiple projects with classified deps" in {
      sbt13only and testProject("classifiers")
    }

    "be same in project with optional dependency" in {
      sbt13only and testProject("optional")
    }

    "be same in play project" in {
      sbt13only and testProject("play", download = false, sbtVersion = "0.13.5")
    }

    "be same in android project" in {
      hasAndroidDefined and testProject("android")
    }

  }
}
