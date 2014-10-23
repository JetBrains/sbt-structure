package org.jetbrains.sbt

import org.scalatest.FunSuite
import java.io.File

/**
 * @author Pavel Fatin
 */
class ImportTest extends FunSuite {
  val DataDir = new File("src/test/data")
  val Exclusions = Seq("sbt-idea", "SbtIdea")

  test("multiple projects with classified deps") {
    doTest("classifiers")
  }

  test("bare projects") {
    doTest("bare", download = false)
  }

  test("android project") {
    doTest("android", download = false)
  }

  test("simple project") {
    doTest("simple")
  }

  test("managed dependency") {
    doTest("dependency")
  }

  test("multiple projects") {
    doTest("multiple")
  }

  private def doTest(project: String, download: Boolean = true, sbtVersion: String = "0.13.0") {
    val base = new File(DataDir, project)

    val actual = Loader.load(base, download).filterNot(s => Exclusions.exists(s.contains)).mkString("\n")

    val expected = {
      val text = read(new File(base, "structure.xml")).mkString("\n")
      val androidHome = Option(System.getenv.get("ANDROID_HOME")) getOrElse ""
      text.replace("$BASE", FS.toPath(base)).replace("$ANDROID_HOME", androidHome)
    }

    if (actual != expected) {
      println("Actual output:\n" + actual)
      fail()
    }
  }
}

